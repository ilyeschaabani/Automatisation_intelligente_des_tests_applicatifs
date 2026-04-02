package com.pfe.platform.testmanagementmicroservice.service.Discovery;


import com.pfe.platform.testmanagementmicroservice.entity.Discovery;
import com.pfe.platform.testmanagementmicroservice.entity.Endpoint;
import com.pfe.platform.testmanagementmicroservice.entity.Project;
import com.pfe.platform.testmanagementmicroservice.repository.DiscoveryRepository;
import com.pfe.platform.testmanagementmicroservice.repository.EndpointRepository;
import com.pfe.platform.testmanagementmicroservice.repository.ProjectRepository;
import com.pfe.platform.testmanagementmicroservice.service.FastAPI.FastApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class DiscoveryServiceimpl {
    private final FastApiClient fastApiClient;
    private final ProjectRepository projectRepo;
    private final DiscoveryRepository discoveryRepo;
    private final EndpointRepository endpointRepo;
    private final ObjectMapper objectMapper;

    public List<Endpoint> getOrStartDiscovery(Long projectId, String branch) {

        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        String finalBranch = (branch != null && !branch.isBlank())
                ? branch
                : (project.getDefaultBranch() != null && !project.getDefaultBranch().isBlank()
                    ? project.getDefaultBranch()
                    : "main");

        // ✅ STEP 1: Check GLOBAL discovery (repoUrl + branch)
        List<Discovery> existingDiscoveries =
                discoveryRepo.findLatestByRepoAndBranch(
                        project.getRepositoryUrl(),
                        finalBranch,
                        "done"
                );

        if (!existingDiscoveries.isEmpty()) {
            System.out.println("♻️ Reusing existing discovery");

            Discovery existing = existingDiscoveries.get(0);

            // 🔥 OPTIONAL: link it to this project
            existing.setProject(project);
            discoveryRepo.save(existing);

            // Ensure endpoints are initialized (existing is not guaranteed to have endpoints loaded).
            return discoveryRepo.findByIdWithEndpoints(existing.getId())
                    .map(Discovery::getEndpoints)
                    .orElse(List.of());
        }

        // ✅ STEP 2: Check if already RUNNING
        Optional<Discovery> running = discoveryRepo.findTopByProjectIdAndBranchOrderByCreatedAtDesc(projectId, finalBranch);
        if (running.isPresent()) {
            String st = running.get().getStatus();
            if ("running".equals(st) || "needs_user_input".equals(st)) {
                System.out.println("⏳ Discovery already in progress (status=" + st + ")...");
                return List.of();
            }
        }

        // ✅ STEP 3: Start new discovery
        System.out.println("🚀 Starting new discovery...");

        Map<String, Object> job = fastApiClient.startDiscovery(
                project.getRepositoryUrl(),
                finalBranch
        );

        Discovery discovery = new Discovery();
        discovery.setProject(project);
        discovery.setRepoUrl(project.getRepositoryUrl());
        discovery.setBranch(finalBranch);
        discovery.setStatus("running");

        Object jobId = job.get("job_id");
        if (jobId instanceof String s && !s.isBlank()) {
            discovery.setFastApiJobId(s);
        }
        Object repoId = job.get("repo_id");
        if (repoId instanceof String s && !s.isBlank()) {
            discovery.setFastApiRepoId(s);
        }

        discoveryRepo.save(discovery);

        CompletableFuture.runAsync(() ->
            pollAndSaveEndpoints(discovery.getId(), (String) job.get("job_id"))
        );

        return List.of();
    }

    private void pollAndSaveEndpoints(Long discoveryId, String jobId) {
        try {
            Discovery discovery = discoveryRepo.findById(discoveryId)
                    .orElseThrow(() -> new RuntimeException("Discovery not found"));

            Map<String, Object> status;
            String state;

            // ⏳ WAIT until job is DONE
            do {
                Thread.sleep(3000); // wait 3s

                status = fastApiClient.getJobStatus(jobId);
                state = (String) status.get("status");

                System.out.println("Job status: " + state);

                if ("error".equals(state)) {
                    throw new RuntimeException("FastAPI job failed: " + status.get("error"));
                }

            } while (!"done".equals(state));

            // ✅ ONLY NOW call OpenAPI
            System.out.println("Fetching OpenAPI...");

            Map<String, Object> openApi = fastApiClient.getJobOpenApi(jobId);

            // If OpenAPI needs runtime info, store questionnaire and wait for user input.
            Map<String, Object> questionnaire = fastApiClient.getContractQuestionnaire(jobId);
            if (hasQuestions(questionnaire) || hasMissingDiscoveryInfo(openApi)) {
                discovery.setStatus("needs_user_input");
                discovery.setContractQuestionnaireJson(safeToJson(questionnaire));
                discoveryRepo.save(discovery);
                System.out.println("🧩 Discovery requires user input (questionnaire stored)");
                return;
            }

            finalizeDiscoveryWithOpenApi(discovery, openApi);
            System.out.println("✅ Discovery completed and endpoints saved!");

        } catch (Exception e) {
            try {
                Discovery discovery = discoveryRepo.findById(discoveryId).orElse(null);
                if (discovery != null) {
                    discovery.setStatus("error");
                    discovery.setError(e.getMessage());
                    discoveryRepo.save(discovery);
                }
            } catch (Exception ignored) {
            }

            System.out.println("❌ Discovery failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void refreshQuestionnaireIfNeeded(Discovery discovery) {
        if (discovery == null) return;
        if (!"needs_user_input".equals(discovery.getStatus())) return;

        String jobId = discovery.getFastApiJobId();
        if (jobId == null || jobId.isBlank()) return;

        boolean shouldRefresh = false;
        String stored = discovery.getContractQuestionnaireJson();
        if (stored != null && !stored.isBlank()) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(stored, Map.class);
                boolean hasStoredQuestions = hasQuestions(parsed);
                // Refresh when there are no questions, or when questions contain legacy object-level paths
                // that cannot be answered as scalars (e.g. $.x-discovery.auth.login).
                shouldRefresh = !hasStoredQuestions || hasLegacyObjectQuestions(parsed);
            } catch (Exception ignored) {
                shouldRefresh = true;
            }
        } else {
            shouldRefresh = true;
        }

        if (!shouldRefresh) return;

        try {
            Map<String, Object> questionnaire = fastApiClient.getContractQuestionnaire(jobId);
            if (hasQuestions(questionnaire)) {
                discovery.setContractQuestionnaireJson(safeToJson(questionnaire));
                discoveryRepo.save(discovery);
                return;
            }

            discovery.setStatus("error");
            discovery.setError("Discovery requires user input but no questions were produced");
            discoveryRepo.save(discovery);
        } catch (Exception e) {
            discovery.setStatus("error");
            discovery.setError("Failed to refresh discovery questionnaire: " + e.getMessage());
            discoveryRepo.save(discovery);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean hasLegacyObjectQuestions(Map<String, Object> questionnaireResp) {
        if (questionnaireResp == null) return false;
        Object qObj = questionnaireResp.get("questionnaire");
        if (!(qObj instanceof Map<?, ?> qMap)) return false;
        Object questionsObj = qMap.get("questions");
        if (!(questionsObj instanceof List<?> list) || list.isEmpty()) return false;

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) continue;
            Object jpObj = row.get("json_path");
            if (!(jpObj instanceof String jp)) continue;
            String p = jp.trim();
            if (p.isEmpty()) continue;

            // Object-valued paths: the UI expects a scalar answer, but these represent objects.
            if ("$.x-discovery.run".equals(p) || "$.x-discovery.auth".equals(p) || "$.x-discovery.auth.login".equals(p)) {
                return true;
            }
        }

        return false;
    }

    public Discovery getLatestDiscovery(Long projectId, String branch) {
        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        String finalBranch = (branch != null && !branch.isBlank())
                ? branch
                : (project.getDefaultBranch() != null && !project.getDefaultBranch().isBlank()
                ? project.getDefaultBranch()
                : "main");

        List<Discovery> list = discoveryRepo.findLatestByProjectAndBranchWithEndpoints(projectId, finalBranch);
        Discovery latest = list.isEmpty() ? null : list.get(0);

        refreshQuestionnaireIfNeeded(latest);

        if (latest == null) return null;
        return discoveryRepo.findByIdWithEndpoints(latest.getId()).orElse(latest);
    }

    public Discovery startDiscoveryFlow(Long projectId, String branch) {
        // Kick off discovery (existing method) and then return latest state.
        getOrStartDiscovery(projectId, branch);
        return getLatestDiscovery(projectId, branch);
    }

    public Discovery completeDiscoveryFlow(Long discoveryId, Map<String, Object> body) {
        Discovery discovery = discoveryRepo.findByIdWithEndpoints(discoveryId)
            .orElseThrow(() -> new RuntimeException("Discovery not found"));

        if (!"needs_user_input".equals(discovery.getStatus())) {
            return discovery;
        }
        if (discovery.getFastApiJobId() == null || discovery.getFastApiJobId().isBlank()) {
            throw new RuntimeException("fastApiJobId is missing for this discovery");
        }

        Map<String, Object> result = fastApiClient.completeDiscovery(discovery.getFastApiJobId(), body);
        Object missingObj = result.get("missing");
        boolean stillMissing = missingObj instanceof List<?> l && !l.isEmpty();

        if (stillMissing) {
            Map<String, Object> questionnaire = fastApiClient.getContractQuestionnaire(discovery.getFastApiJobId());
            // Only keep needs_user_input when there are actual questions.
            if (hasQuestions(questionnaire)) {
                discovery.setStatus("needs_user_input");
                discovery.setContractQuestionnaireJson(safeToJson(questionnaire));
                discoveryRepo.save(discovery);
                return discoveryRepo.findByIdWithEndpoints(discoveryId).orElse(discovery);
            }

            discovery.setStatus("error");
            discovery.setError("Discovery still missing required values but no questions were produced");
            discoveryRepo.save(discovery);
            return discovery;
        }

        // Contract complete: fetch OpenAPI and persist endpoints
        Map<String, Object> openApi = fastApiClient.getJobOpenApi(discovery.getFastApiJobId());
        finalizeDiscoveryWithOpenApi(discovery, openApi);
        return discoveryRepo.findByIdWithEndpoints(discoveryId).orElse(discovery);
    }

    private boolean hasQuestions(Map<String, Object> questionnaireResp) {
        if (questionnaireResp == null) return false;
        Object qObj = questionnaireResp.get("questionnaire");
        if (!(qObj instanceof Map<?, ?> qMap)) return false;
        Object questionsObj = qMap.get("questions");
        return questionsObj instanceof List<?> l && !l.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private boolean hasMissingDiscoveryInfo(Map<String, Object> openApi) {
        if (openApi == null) return false;
        Object xd = openApi.get("x-discovery");
        if (!(xd instanceof Map<?, ?> xdisc)) return false;
        Object disc = xdisc.get("discovery");
        if (!(disc instanceof Map<?, ?> d)) return false;
        Object missing = d.get("missing");
        return missing instanceof List<?> l && !l.isEmpty();
    }

    private String safeToJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void finalizeDiscoveryWithOpenApi(Discovery discovery, Map<String, Object> openApi) {
        // Clear previously saved endpoints for idempotency.
        List<Endpoint> existing = endpointRepo.findByDiscovery(discovery);
        if (!existing.isEmpty()) {
            endpointRepo.deleteAll(existing);
        }

        Object pathsObj = openApi != null ? openApi.get("paths") : null;
        if (!(pathsObj instanceof Map<?, ?> paths)) {
            discovery.setStatus("error");
            discovery.setError("OpenAPI missing 'paths'");
            discoveryRepo.save(discovery);
            return;
        }

        for (Object pathKey : paths.keySet()) {
            if (!(pathKey instanceof String path)) continue;
            Object opsObj = paths.get(pathKey);
            if (!(opsObj instanceof Map<?, ?> ops)) continue;

            for (Object methodKey : ops.keySet()) {
                if (!(methodKey instanceof String method)) continue;
                if (method.startsWith("x-")) continue;

                Object opObj = ops.get(methodKey);
                if (!(opObj instanceof Map<?, ?> op)) continue;

                Endpoint endpoint = new Endpoint();
                endpoint.setDiscovery(discovery);
                endpoint.setMethod(method.toUpperCase());
                endpoint.setPath(path);
                endpoint.setSummary(op.get("summary") instanceof String s ? s : null);
                endpoint.setSource(op.get("x-source") instanceof String s ? s : null);

                Object confidenceObj = op.get("x-confidence");
                if (confidenceObj instanceof Number n) {
                    endpoint.setConfidence(n.doubleValue());
                } else {
                    endpoint.setConfidence(1.0);
                }

                Object metadataObj = op.get("x-metadata");
                if (metadataObj instanceof Map<?, ?> metadata) {
                    Object reqObj = metadata.get("request");
                    endpoint.setRequestSchema(reqObj != null ? reqObj.toString() : null);
                }

                endpointRepo.save(endpoint);
            }
        }

        discovery.setStatus("done");
        discovery.setContractQuestionnaireJson(null);
        discoveryRepo.save(discovery);
    }

}
