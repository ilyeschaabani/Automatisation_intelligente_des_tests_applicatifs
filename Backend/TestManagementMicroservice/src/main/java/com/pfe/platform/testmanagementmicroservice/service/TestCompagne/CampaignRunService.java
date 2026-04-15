package com.pfe.platform.testmanagementmicroservice.service.TestCompagne;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.platform.testmanagementmicroservice.DTO.*;
import com.pfe.platform.testmanagementmicroservice.entity.*;
import com.pfe.platform.testmanagementmicroservice.entity.Enum.ExecutionStatus;
import com.pfe.platform.testmanagementmicroservice.entity.Enum.ExecutionType;
import com.pfe.platform.testmanagementmicroservice.repository.DiscoveryRepository;
import com.pfe.platform.testmanagementmicroservice.repository.EndpointRepository;
import com.pfe.platform.testmanagementmicroservice.repository.TestCampaignRepository;
import com.pfe.platform.testmanagementmicroservice.repository.TestExecutionRepository;
import com.pfe.platform.testmanagementmicroservice.service.DockerComposeRuntime.DockerComposeRuntimeService;
import com.pfe.platform.testmanagementmicroservice.service.TestExecusion.TestExecutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;
import org.yaml.snakeyaml.Yaml;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignRunService {

    private final TestCampaignRepository testCampaignRepository;
    private final DiscoveryRepository discoveryRepository;
    private final EndpointRepository endpointRepository;
    private final TestExecutionRepository testExecutionRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DockerComposeRuntimeService dockerComposeRuntimeService;

    private final ConcurrentMap<String, Integer> hostPortBaseBySession = new ConcurrentHashMap<>();

    @Value("${discovery.base-url}")
    private String discoveryBaseUrl;

    @Value("${run.api.base-url:http://127.0.0.1}")
    private String runApiBaseUrl;

    @Value("${run.api.health-path:/health}")
    private String runApiHealthPath;

    @Value("${run.api.default-port:}")
    private String runApiDefaultPort;

    @Value("${run.api.health-timeout-seconds:180}")
    private long runApiHealthTimeoutSeconds;

    @Value("${run.api.health-poll-interval-ms:2000}")
    private long runApiHealthPollIntervalMs;

    @Value("${run.compose.start-timeout-seconds:600}")
    private long runComposeStartTimeoutSeconds;

    @Value("${run.compose.cleanup-timeout-seconds:240}")
    private long runComposeCleanupTimeoutSeconds;

    @Value("${run.dynamic-host-port-enabled:false}")
    private boolean dynamicHostPortEnabled;

    @Transactional
    public ResponseEntity<CampaignRunResponse> run(Long campaignId, CampaignRunRequest request) {
        TestCampaign campaign = loadCampaign(campaignId);
        Project project = campaign.getProject();
        String repoUrl = requireRepositoryUrl(project);

        CampaignRunRequest req = request != null
                ? request
                : new CampaignRunRequest(null, null, null, null, null, null);

        String branch = firstNonBlank(req.branch(), project.getDefaultBranch(), "main");
        Integer effectiveHostPortBase = resolveHostPortBaseForInitialRun(req.hostPortBase());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("repo_url", repoUrl);
        payload.put("branch", branch);
        fillCommonPayload(
                payload,
                req.db(),
                req.envValues(),
                effectiveHostPortBase,
                req.useOllama(),
                req.ollamaModel()
        );

        return callCloneRepo(campaign, project, repoUrl, payload, null, effectiveHostPortBase, false, null);
    }

    @Transactional
    public ResponseEntity<CampaignRunResponse> continueRun(Long campaignId, CampaignRunContinueRequest request) {
        String incomingSessionId = request == null ? null : trimToNull(request.sessionId());
        if (incomingSessionId == null) {
            throw new IllegalArgumentException("sessionId is required");
        }
        log.info("continueRun: sessionId={}, db={}, envValues={}", incomingSessionId, request.db(), request.envValues());
        if (request.db() == null || request.db().isBlank()) {
            log.warn("continueRun: db is missing or blank!");
        }
        if (request.envValues() == null || request.envValues().isEmpty()) {
            log.warn("continueRun: envValues is missing or empty!");
        }

        TestCampaign campaign = loadCampaign(campaignId);
        Project project = campaign.getProject();
        String repoUrl = requireRepositoryUrl(project);

        Integer effectiveHostPortBase = resolveHostPortBaseForContinue(incomingSessionId, request.hostPortBase());

        Map<String, Object> payload = new LinkedHashMap<>();
        fillCommonPayload(
                payload,
                request.db(),
                request.envValues(),
                effectiveHostPortBase,
                request.useOllama(),
                request.ollamaModel()
        );

        return callCloneRepo(
                campaign,
                project,
                repoUrl,
                payload,
                incomingSessionId,
            effectiveHostPortBase,
            true,
            request.fileOverrides()
        );
    }

    @Transactional
    public void cleanupRun(Long campaignId, Long executionId) {
        TestExecution execution = testExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        if (execution.getCampaign() == null || !Objects.equals(execution.getCampaign().getId(), campaignId)) {
            throw new IllegalArgumentException("Execution " + executionId + " does not belong to campaign " + campaignId);
        }

        if (execution.getCleanedAt() != null) {
            return;
        }

        String repoPathRaw = trimToNull(execution.getTempRepoPath());
        String composePathRaw = trimToNull(execution.getComposePath());
        String composeProjectName = trimToNull(execution.getComposeProjectName());

        if (repoPathRaw == null || composePathRaw == null || composeProjectName == null) {
            throw new IllegalArgumentException("Execution does not have runtime metadata for cleanup");
        }

        Path repoPath = Paths.get(repoPathRaw);
        Path composePath = Paths.get(composePathRaw);

        try {
            dockerComposeRuntimeService.composeDownAndCleanup(
                    repoPath,
                    composePath,
                    composeProjectName,
                    normalizeBaseUrl(discoveryBaseUrl),
                    trimToNull(execution.getRunSessionId()),
                    Duration.ofSeconds(runComposeCleanupTimeoutSeconds)
            );

            execution.setStatus(ExecutionStatus.FINISHED);
            execution.setRuntimeError(null);
        } catch (Exception ex) {
            execution.setStatus(ExecutionStatus.ERROR);
            execution.setRuntimeError(truncate(ex.getMessage(), 4000));
            throw new IllegalArgumentException("Cleanup failed: " + ex.getMessage(), ex);
        } finally {
            execution.setCleanedAt(Instant.now());
            testExecutionRepository.save(execution);

            String sid = trimToNull(execution.getRunSessionId());
            if (sid != null) {
                hostPortBaseBySession.remove(sid);
            }
        }
    }

    private ResponseEntity<CampaignRunResponse> callCloneRepo(
            TestCampaign campaign,
            Project project,
            String repoUrl,
            Map<String, Object> payload,
            String incomingSessionId,
            Integer hostPortBase,
            boolean startCompose,
            Map<String, String> fileOverrides
    ) {
        String base = normalizeBaseUrl(discoveryBaseUrl);
        String url = (incomingSessionId == null)
                ? base + "/prepare"
                : base + "/continue/" + UriUtils.encodePathSegment(incomingSessionId, StandardCharsets.UTF_8);

        try {
            log.info("Payload sent to Python service: {}", payload);
            Map<String, Object> result = postJson(url, payload);

            CampaignRunResponse response = startCompose
                    ? handleStartedSuccess(campaign, project, repoUrl, result, hostPortBase, fileOverrides)
                    : handlePreparedSuccess(campaign, project, repoUrl, result, hostPortBase);

            String sid = trimToNull(response.sessionId());
            if (sid != null) {
                if (startCompose && "started".equalsIgnoreCase(trimToNull(response.status()))) {
                    hostPortBaseBySession.remove(sid);
                } else if (hostPortBase != null) {
                    // Preserve hostPortBase for the follow-up /run/continue.
                    hostPortBaseBySession.put(sid, hostPortBase);
                }
            }
            return ResponseEntity.ok(response);

        } catch (HttpStatusCodeException ex) {
            log.error("Python service error {}: {}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().value() == 409) {
                CampaignRunResponse response = handleNeedsInput(ex);
                String sid = trimToNull(response.sessionId());
                if (sid != null && hostPortBase != null) {
                    hostPortBaseBySession.put(sid, hostPortBase);
                }
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }

            String message = trimToNull(ex.getResponseBodyAsString());
            if (message == null) {
                message = "clone_repo call failed with HTTP " + ex.getStatusCode().value();
            }
            throw new IllegalArgumentException(message);
        }
    }

    private Map<String, Object> postJson(String url, Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        if (response.getBody() == null) {
            return new LinkedHashMap<>();
        }

        return toStringKeyMap(response.getBody());
    }

    private CampaignRunResponse handleNeedsInput(HttpStatusCodeException ex) {
        Map<String, Object> root = readErrorMap(ex.getResponseBodyAsString());
        Map<String, Object> detail = asMap(root.get("detail"));
        if (detail.isEmpty()) {
            detail = root;
        }

        String sessionId = trimToNull(asString(detail.get("session_id")));
        boolean missingDb = asBoolean(detail.get("missing_db"), false);
        List<String> missingEnvVars = asStringList(detail.get("missing_env_vars"));
        List<String> dbOptions = asStringList(detail.get("db_options"));
        List<String> notes = asStringList(detail.get("notes"));

        List<EditableFileDto> editableFiles = List.of();
        String repoPathRaw = trimToNull(asString(detail.get("repo_path")));
        String composePathRaw = trimToNull(asString(detail.get("compose_path")));
        if (repoPathRaw != null && composePathRaw != null) {
            try {
                Path repoPath = Paths.get(repoPathRaw);
                Path composePath = Paths.get(composePathRaw);
                if (!composePath.isAbsolute()) {
                    composePath = repoPath.resolve(composePath).normalize();
                }
                editableFiles = loadEditableFiles(repoPath, composePath);
            } catch (Exception ignored) {
            }
        }

        return new CampaignRunResponse(
                "needs_user_input",
                "Run requires additional inputs",
                sessionId,
                null,
                List.of(),
                missingDb,
                missingEnvVars,
                dbOptions,
                notes,
                editableFiles
        );
    }

    private CampaignRunResponse handlePreparedSuccess(
            TestCampaign campaign,
            Project project,
            String repoUrl,
            Map<String, Object> result,
            Integer hostPortBase
    ) {
        String sessionId = trimToNull(asString(result.get("session_id")));
        String repoPathRaw = trimToNull(asString(result.get("repo_path")));
        String composePathRaw = trimToNull(asString(result.get("compose_path")));
        String branch = firstNonBlank(trimToNull(asString(result.get("branch"))), "main");

        if (sessionId == null) {
            throw new IllegalArgumentException("clone_repo did not return session_id");
        }
        if (repoPathRaw == null || composePathRaw == null) {
            throw new IllegalArgumentException("clone_repo did not return repo_path/compose_path");
        }

        Path repoPath = Paths.get(repoPathRaw);
        Path composePath = Paths.get(composePathRaw);
        if (!composePath.isAbsolute()) {
            composePath = repoPath.resolve(composePath).normalize();
        }

        List<Map<String, Object>> rawEndpoints = asMapList(result.get("endpoints"));
        List<String> notes = new ArrayList<>(asStringList(result.get("notes")));

        List<EndpointDto> endpointDtos = persistDiscoveryAndEndpoints(project, repoUrl, branch, rawEndpoints);
        List<EditableFileDto> editableFiles = loadEditableFiles(repoPath, composePath);

        return new CampaignRunResponse(
                "needs_review",
                "Review docker-compose.yml and Dockerfiles before starting the run",
                sessionId,
                null,
                endpointDtos,
                false,
                List.of(),
                List.of(),
                notes,
                editableFiles
        );
    }

    private CampaignRunResponse handleStartedSuccess(
            TestCampaign campaign,
            Project project,
            String repoUrl,
            Map<String, Object> result,
            Integer hostPortBase,
            Map<String, String> fileOverrides
    ) {
        String sessionId = trimToNull(asString(result.get("session_id")));
        String repoPathRaw = trimToNull(asString(result.get("repo_path")));
        String composePathRaw = trimToNull(asString(result.get("compose_path")));
        String apiService = trimToNull(asString(result.get("api_service")));
        String branch = firstNonBlank(trimToNull(asString(result.get("branch"))), "main");

        if (sessionId == null) {
            throw new IllegalArgumentException("clone_repo did not return session_id");
        }
        if (repoPathRaw == null || composePathRaw == null) {
            throw new IllegalArgumentException("clone_repo did not return repo_path/compose_path");
        }

        Path repoPath = Paths.get(repoPathRaw);
        Path composePath = Paths.get(composePathRaw);
        if (!composePath.isAbsolute()) {
            composePath = repoPath.resolve(composePath).normalize();
        }

        List<Map<String, Object>> rawEndpoints = asMapList(result.get("endpoints"));
        List<String> notes = new ArrayList<>(asStringList(result.get("notes")));

        List<EndpointDto> endpointDtos = persistDiscoveryAndEndpoints(project, repoUrl, branch, rawEndpoints);

        TestExecution execution = new TestExecution();
        execution.setCampaign(campaign);
        execution.setExecutionType(ExecutionType.INITIAL);
        execution.setStatus(ExecutionStatus.QUEUED);
        execution = testExecutionRepository.save(execution);

        String composeProjectName = dockerComposeRuntimeService.newProjectName(execution.getId());
        Integer apiPort = resolveApiPort(hostPortBase);
        URI requestedHealthUri = buildHealthUri(apiPort);

        execution.setRunSessionId(sessionId);
        execution.setTempRepoPath(repoPath.toString());
        execution.setComposePath(composePath.toString());
        execution.setComposeProjectName(composeProjectName);
        execution.setHealthUrl(requestedHealthUri.toString());
        execution = testExecutionRepository.save(execution);

        try {
            List<String> applied = applyFileOverrides(repoPath, composePath, fileOverrides);
            if (!applied.isEmpty()) {
                notes.add("Applied overrides: " + String.join(", ", applied));
            }

            URI effectiveHealthUri = dockerComposeRuntimeService.composeUpAndWait(
                    repoPath,
                    composePath,
                    composeProjectName,
                    requestedHealthUri,
                    apiService,
                    Duration.ofSeconds(runComposeStartTimeoutSeconds),
                    Duration.ofSeconds(runApiHealthTimeoutSeconds),
                    Duration.ofMillis(runApiHealthPollIntervalMs)
            );

            // Persist the resolved URL (may differ when compose exposes a dynamic host port).
            execution.setHealthUrl(effectiveHealthUri.toString());

            execution.setStatus(ExecutionStatus.RUNNING);
            execution.setRuntimeError(null);
            execution = testExecutionRepository.save(execution);

            notes.add("compose_project=" + composeProjectName);
            if (apiService != null) {
                notes.add("api_service=" + apiService);
            }
            notes.add("health_url=" + effectiveHealthUri);
            if (hostPortBase != null) {
                notes.add("host_port_base=" + hostPortBase);
            }

        } catch (Exception ex) {
            execution.setStatus(ExecutionStatus.ERROR);
            execution.setRuntimeError(truncate(ex.getMessage(), 4000));
            testExecutionRepository.save(execution);

            throw new IllegalArgumentException("Docker startup failed: " + ex.getMessage(), ex);
        }

        TestExecutionDto executionDto = TestExecutionMapper.toDto(execution);

        return new CampaignRunResponse(
                "started",
                "Campaign run started",
                sessionId,
                executionDto,
                endpointDtos,
                false,
                List.of(),
                List.of(),
                notes,
                List.of()
        );
    }

    private List<EditableFileDto> loadEditableFiles(Path repoPath, Path composePath) {
        try {
            Path repo = repoPath.toAbsolutePath().normalize();
            Path compose = composePath.toAbsolutePath().normalize();

            List<EditableFileDto> out = new ArrayList<>();

            String composeRel = toRepoRelativePath(repo, compose);
            String composeContent = safeReadUtf8(compose);
            if (composeContent != null) {
                out.add(new EditableFileDto(composeRel, composeContent));
            }

            for (Path dockerfile : findDockerfilesFromCompose(repo, compose)) {
                String rel = toRepoRelativePath(repo, dockerfile);
                String content = safeReadUtf8(dockerfile);
                out.add(new EditableFileDto(rel, content != null ? content : ""));
            }

            return out;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Path> findDockerfilesFromCompose(Path repoPath, Path composePath) {
        String composeText = safeReadUtf8(composePath);
        if (composeText == null) {
            return List.of();
        }

        Object data;
        try {
            data = new Yaml().load(composeText);
        } catch (Exception ex) {
            return List.of();
        }
        if (!(data instanceof Map<?, ?> root)) {
            return List.of();
        }

        Object servicesObj = root.get("services");
        if (!(servicesObj instanceof Map<?, ?> services)) {
            return List.of();
        }

        Path composeDir = composePath.getParent();
        if (composeDir == null) {
            return List.of();
        }

        Path repo = repoPath.toAbsolutePath().normalize();
        List<Path> out = new ArrayList<>();
        Set<Path> seen = new LinkedHashSet<>();

        for (Object svcObj : services.values()) {
            if (!(svcObj instanceof Map<?, ?> svc)) {
                continue;
            }

            Object buildObj = svc.get("build");
            if (buildObj == null) {
                continue;
            }

            String contextRel;
            String dockerfileRel;

            if (buildObj instanceof String s) {
                contextRel = s;
                dockerfileRel = "Dockerfile";
            } else if (buildObj instanceof Map<?, ?> buildMap) {
                Object ctxObj = buildMap.get("context");
                contextRel = (ctxObj instanceof String ss) ? ss : ".";

                Object dfObj = buildMap.get("dockerfile");
                dockerfileRel = (dfObj instanceof String ss) ? ss : "Dockerfile";
            } else {
                continue;
            }

            try {
                Path contextDir = composeDir.resolve(contextRel).normalize();
                Path dockerfile = contextDir.resolve(dockerfileRel).normalize().toAbsolutePath();
                if (!dockerfile.startsWith(repo)) {
                    continue;
                }
                if (seen.add(dockerfile)) {
                    out.add(dockerfile);
                }
            } catch (Exception ignored) {
            }
        }

        return out;
    }

    private List<String> applyFileOverrides(Path repoPath, Path composePath, Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return List.of();
        }

        Path repo = repoPath.toAbsolutePath().normalize();
        Path compose = composePath.toAbsolutePath().normalize();
        String composeRel = normalizeOverrideKey(toRepoRelativePath(repo, compose));

        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : overrides.entrySet()) {
            String key = trimToNull(e.getKey());
            String value = e.getValue();
            if (key != null && value != null) {
                sanitized.put(normalizeOverrideKey(key), value);
            }
        }
        if (sanitized.isEmpty()) {
            return List.of();
        }

        List<String> applied = new ArrayList<>();

        // Apply compose override first (if any), then compute allowed Dockerfiles.
        String composeOverride = sanitized.get(composeRel);
        if (composeOverride != null) {
            safeWriteUtf8(compose, composeOverride);
            applied.add(composeRel);
        }

        Map<String, Path> allowedDockerfilesByRel = new LinkedHashMap<>();
        for (Path dockerfile : findDockerfilesFromCompose(repo, compose)) {
            String rel = normalizeOverrideKey(toRepoRelativePath(repo, dockerfile));
            allowedDockerfilesByRel.put(rel, dockerfile);
        }

        for (Map.Entry<String, String> e : sanitized.entrySet()) {
            String rel = e.getKey();
            if (composeRel.equals(rel)) {
                continue;
            }

            Path target = allowedDockerfilesByRel.get(rel);
            if (target == null) {
                throw new IllegalArgumentException("Override path is not a docker artifact: " + rel);
            }

            safeWriteUtf8(target, e.getValue());
            applied.add(rel);
        }

        return applied;
    }

    private Path resolveUnderRepo(Path repoPath, String repoRelativePath) {
        String rel = trimToNull(repoRelativePath);
        if (rel == null) {
            throw new IllegalArgumentException("override path is required");
        }
        if (rel.contains("\u0000")) {
            throw new IllegalArgumentException("Invalid override path");
        }

        Path p;
        try {
            p = Paths.get(rel);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid override path: " + rel);
        }

        if (p.isAbsolute()) {
            throw new IllegalArgumentException("Override path must be repo-relative: " + rel);
        }

        Path resolved = repoPath.resolve(p).normalize().toAbsolutePath();
        if (!resolved.startsWith(repoPath)) {
            throw new IllegalArgumentException("Override path escapes repo: " + rel);
        }
        return resolved;
    }

    private String normalizeOverrideKey(String raw) {
        String key = trimToNull(raw);
        if (key == null) {
            return "";
        }

        key = key.replace('\\', '/').trim();
        while (key.startsWith("./")) {
            key = key.substring(2);
        }
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        while (key.contains("//")) {
            key = key.replace("//", "/");
        }
        return key;
    }

    private String toRepoRelativePath(Path repoPath, Path absolutePath) {
        try {
            Path rel = repoPath.relativize(absolutePath);
            String out = rel.toString().replace('\\', '/');
            return out.isEmpty() ? absolutePath.getFileName().toString() : out;
        } catch (Exception ex) {
            return absolutePath.getFileName().toString();
        }
    }

    private String safeReadUtf8(Path path) {
        try {
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return null;
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            try {
                return Files.readString(path);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private void safeWriteUtf8(Path path, String content) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to write override file: " + path.getFileName(), ex);
        }
    }

    private List<EndpointDto> persistDiscoveryAndEndpoints(
            Project project,
            String repoUrl,
            String branch,
            List<Map<String, Object>> rawEndpoints
    ) {
        clearExistingDiscoveries(project.getId(), branch);

        Discovery discovery = new Discovery();
        discovery.setProject(project);
        discovery.setRepoUrl(repoUrl);
        discovery.setBranch(branch);
        discovery.setStatus("done");
        discovery = discoveryRepository.save(discovery);

        List<EndpointDto> out = new ArrayList<>();

        for (Map<String, Object> raw : rawEndpoints) {
            String method = trimToNull(asString(raw.get("method")));
            String path = firstNonBlank(
                    asString(raw.get("full_path")),
                    asString(raw.get("path"))
            );

            if (method == null || path == null) {
                continue;
            }

            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            Endpoint endpoint = new Endpoint();
            endpoint.setDiscovery(discovery);
            endpoint.setMethod(method.toUpperCase(Locale.ROOT));
            endpoint.setPath(path);

            String summary = firstNonBlank(
                    asString(raw.get("summary")),
                    asString(raw.get("function_name"))
            );
            endpoint.setSummary(trimToNull(summary));
            endpoint.setSource(trimToNull(asString(raw.get("source"))));
            endpoint.setConfidence(asDouble(raw.get("confidence"), 1.0));
            endpoint.setRequestSchema(extractRequestSchema(raw.get("metadata")));

            Endpoint saved = endpointRepository.save(endpoint);
            out.add(EndpointDto.fromEntity(saved));
        }

        return out;
    }

    private void clearExistingDiscoveries(Long projectId, String branch) {
        List<Discovery> existing = discoveryRepository.findLatestByProjectAndBranchWithEndpoints(projectId, branch);
        for (Discovery discovery : existing) {
            List<Endpoint> endpoints = endpointRepository.findByDiscovery(discovery);
            if (!endpoints.isEmpty()) {
                endpointRepository.deleteAll(endpoints);
            }
            discoveryRepository.delete(discovery);
        }
    }

    private void fillCommonPayload(
            Map<String, Object> payload,
            String db,
            Map<String, String> envValues,
            Integer hostPortBase,
            Boolean useOllama,
            String ollamaModel
    ) {
        payload.put("fail_on_missing", true);
        payload.put("interactive", false);
        payload.put("generate_openapi", true);
        payload.put("auto_assign_host_ports", true);

        String dbValue = trimToNull(db);
        if (dbValue != null) {
            payload.put("db", dbValue.toLowerCase(Locale.ROOT));
        }

        Map<String, String> sanitizedEnv = sanitizeEnvValues(envValues);
        payload.put("env_values", sanitizedEnv); // Always include, even if empty

        if (hostPortBase != null) {
            payload.put("host_port_base", hostPortBase);
        }

        if (Boolean.TRUE.equals(useOllama)) {
            payload.put("use_ollama", true);
        }

        String model = trimToNull(ollamaModel);
        if (model != null) {
            payload.put("ollama_model", model);
        }
    }

    private Integer resolveHostPortBaseForInitialRun(Integer requested) {
        if (requested != null) return requested;
        if (dynamicHostPortEnabled) {
            return dockerComposeRuntimeService.findAvailablePort();
        }
        return null;
    }

    private Integer resolveHostPortBaseForContinue(String sessionId, Integer requested) {
        if (requested != null) return requested;

        Integer remembered = hostPortBaseBySession.get(sessionId);
        if (remembered != null) return remembered;

        if (dynamicHostPortEnabled) {
            return dockerComposeRuntimeService.findAvailablePort();
        }
        return null;
    }

    private Integer resolveApiPort(Integer hostPortBase) {
        if (hostPortBase != null) {
            return hostPortBase;
        }

        Integer configured = parseNullableInt(runApiDefaultPort);
        if (configured != null) {
            return configured;
        }

        throw new IllegalArgumentException(
                "Unable to resolve API port. Set run.api.default-port or provide hostPortBase in run request."
        );
    }

    private URI buildHealthUri(Integer apiPort) {
        String base = trimToNull(runApiBaseUrl);
        if (base == null) {
            throw new IllegalArgumentException("run.api.base-url is required");
        }

        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        if (base.matches(".*:\\d+$")) {
            throw new IllegalArgumentException("run.api.base-url must not include a port. Configure host only.");
        }

        String path = normalizeHealthPath(runApiHealthPath);
        return URI.create(base + ":" + apiPort + path);
    }

    private String normalizeHealthPath(String path) {
        String out = trimToNull(path);
        if (out == null) {
            return "/health";
        }
        return out.startsWith("/") ? out : "/" + out;
    }

    private Integer parseNullableInt(String raw) {
        String text = trimToNull(raw);
        if (text == null) return null;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer value: " + text, ex);
        }
    }

    private Map<String, String> sanitizeEnvValues(Map<String, String> envValues) {
        Map<String, String> out = new LinkedHashMap<>();
        if (envValues == null) {
            return out;
        }

        for (Map.Entry<String, String> entry : envValues.entrySet()) {
            String key = trimToNull(entry.getKey());
            String value = trimToNull(entry.getValue());
            if (key != null && value != null) {
                out.put(key, value);
            }
        }

        return out;
    }

    private String extractRequestSchema(Object metadataObj) {
        if (!(metadataObj instanceof Map<?, ?> metadataRaw)) {
            return null;
        }

        Map<String, Object> metadata = toStringKeyMap(metadataRaw);
        Object request = metadata.get("request");
        if (request == null) {
            return null;
        }

        if (request instanceof String s) {
            return trimToNull(s);
        }

        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception ignored) {
            return String.valueOf(request);
        }
    }

    private Map<String, Object> readErrorMap(String raw) {
        String text = trimToNull(raw);
        if (text == null) {
            return new LinkedHashMap<>();
        }

        try {
            return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("raw", text);
            return fallback;
        }
    }

    private TestCampaign loadCampaign(Long campaignId) {
        return testCampaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));
    }

    private String requireRepositoryUrl(Project project) {
        String repoUrl = trimToNull(project != null ? project.getRepositoryUrl() : null);
        if (repoUrl == null) {
            throw new IllegalArgumentException("Campaign project has no repositoryUrl");
        }
        return repoUrl;
    }

    private String normalizeBaseUrl(String value) {
        String out = value == null ? "" : value.trim();
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String cleaned = trimToNull(value);
            if (cleaned != null) {
                return cleaned;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String out = value.trim();
        return out.isEmpty() ? null : out;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean asBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return fallback;
        }
        String raw = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(raw) || "1".equals(raw) || "yes".equals(raw) || "y".equals(raw)) {
            return true;
        }
        if ("false".equals(raw) || "0".equals(raw) || "no".equals(raw) || "n".equals(raw)) {
            return false;
        }
        return fallback;
    }

    private double asDouble(Object value, double fallback) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<String> out = new ArrayList<>();
        for (Object item : list) {
            String v = trimToNull(asString(item));
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }

    private List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> raw) {
                out.add(toStringKeyMap(raw));
            }
        }
        return out;
    }

    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return new LinkedHashMap<>();
        }
        return toStringKeyMap(raw);
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return out;
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}