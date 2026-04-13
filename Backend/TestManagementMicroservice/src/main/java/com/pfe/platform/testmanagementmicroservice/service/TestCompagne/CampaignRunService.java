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

import com.pfe.platform.testmanagementmicroservice.service.TestExecusion.TestExecutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;



@Service
@RequiredArgsConstructor
public class CampaignRunService {
    private final TestCampaignRepository testCampaignRepository;
    private final DiscoveryRepository discoveryRepository;
    private final EndpointRepository endpointRepository;
    private final TestExecutionRepository testExecutionRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${discovery.base-url}")
    private String discoveryBaseUrl;

    @Transactional
    public ResponseEntity<CampaignRunResponse> run(Long campaignId, CampaignRunRequest request) {
        TestCampaign campaign = loadCampaign(campaignId);
        Project project = campaign.getProject();
        String repoUrl = requireRepositoryUrl(project);

        CampaignRunRequest req = request != null
                ? request
                : new CampaignRunRequest(null, null, null, null, null, null);

        String branch = firstNonBlank(req.branch(), project.getDefaultBranch(), "main");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("repo_url", repoUrl);
        payload.put("branch", branch);
        fillCommonPayload(payload, req.db(), req.envValues(), req.hostPortBase(), req.useOllama(), req.ollamaModel());

        return callCloneRepo(campaign, project, repoUrl, branch, payload, null);
    }

    @Transactional
    public ResponseEntity<CampaignRunResponse> continueRun(Long campaignId, CampaignRunContinueRequest request) {
        if (request == null || trimToNull(request.sessionId()) == null) {
            throw new IllegalArgumentException("sessionId is required");
        }

        TestCampaign campaign = loadCampaign(campaignId);
        Project project = campaign.getProject();
        String repoUrl = requireRepositoryUrl(project);
        String branch = firstNonBlank(project.getDefaultBranch(), "main");

        Map<String, Object> payload = new LinkedHashMap<>();
        fillCommonPayload(payload, request.db(), request.envValues(), request.hostPortBase(), request.useOllama(), request.ollamaModel());

        return callCloneRepo(campaign, project, repoUrl, branch, payload, trimToNull(request.sessionId()));
    }

    private ResponseEntity<CampaignRunResponse> callCloneRepo(
            TestCampaign campaign,
            Project project,
            String repoUrl,
            String branch,
            Map<String, Object> payload,
            String sessionId
    ) {
        String base = normalizeBaseUrl(discoveryBaseUrl);
        String url = (sessionId == null)
                ? base + "/prepare"
                : base + "/continue/" + UriUtils.encodePathSegment(sessionId, StandardCharsets.UTF_8);

        try {
            Map<String, Object> result = postJson(url, payload);
            CampaignRunResponse response = handleSuccess(campaign, project, repoUrl, branch, result);
            return ResponseEntity.ok(response);
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 409) {
                CampaignRunResponse response = handleNeedsInput(ex);
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

        return new CampaignRunResponse(
                "needs_user_input",
                "Run requires additional inputs",
                sessionId,
                null,
                List.of(),
                missingDb,
                missingEnvVars,
                dbOptions,
                notes
        );
    }

    private CampaignRunResponse handleSuccess(
            TestCampaign campaign,
            Project project,
            String repoUrl,
            String branch,
            Map<String, Object> result
    ) {
        String sessionId = trimToNull(asString(result.get("session_id")));
        List<Map<String, Object>> rawEndpoints = asMapList(result.get("endpoints"));
        List<String> notes = asStringList(result.get("notes"));

        List<EndpointDto> endpointDtos = persistDiscoveryAndEndpoints(project, repoUrl, branch, rawEndpoints);

        TestExecution execution = new TestExecution();
        execution.setCampaign(campaign);
        execution.setExecutionType(ExecutionType.INITIAL);
        execution.setStatus(ExecutionStatus.QUEUED);
        TestExecution savedExecution = testExecutionRepository.save(execution);
        TestExecutionDto executionDto = TestExecutionMapper.toDto(savedExecution);

        cleanupSessionQuietly(sessionId);

        return new CampaignRunResponse(
                "started",
                "Campaign run started",
                sessionId,
                executionDto,
                endpointDtos,
                false,
                List.of(),
                List.of(),
                notes
        );
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
        if (!sanitizedEnv.isEmpty()) {
            payload.put("env_values", sanitizedEnv);
        }

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

    private void cleanupSessionQuietly(String sessionId) {
        if (trimToNull(sessionId) == null) {
            return;
        }

        String url = normalizeBaseUrl(discoveryBaseUrl)
                + "/done/"
                + UriUtils.encodePathSegment(sessionId, StandardCharsets.UTF_8);

        try {
            restTemplate.postForEntity(url, HttpEntity.EMPTY, Map.class);
        } catch (Exception ignored) {
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


}
