package com.pfe.platform.testmanagementmicroservice.service.Discovery;


import com.pfe.platform.testmanagementmicroservice.entity.Discovery;
import com.pfe.platform.testmanagementmicroservice.entity.Endpoint;
import com.pfe.platform.testmanagementmicroservice.entity.Project;
import com.pfe.platform.testmanagementmicroservice.repository.DiscoveryRepository;
import com.pfe.platform.testmanagementmicroservice.repository.EndpointRepository;
import com.pfe.platform.testmanagementmicroservice.repository.ProjectRepository;
import com.pfe.platform.testmanagementmicroservice.service.FastAPI.FastApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
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

            return existing.getEndpoints();
        }

        // ✅ STEP 2: Check if already RUNNING
        Optional<Discovery> running =
                discoveryRepo.findTopByProjectIdAndBranchAndStatusOrderByCreatedAtDesc(
                        projectId,
                        finalBranch,
                        "running"
                );

        if (running.isPresent()) {
            System.out.println("⏳ Discovery already running...");
            return List.of();
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

        discoveryRepo.save(discovery);

        CompletableFuture.runAsync(() ->
                pollAndSaveEndpoints(discovery, (String) job.get("job_id"))
        );

        return List.of();
    }

    private void pollAndSaveEndpoints(Discovery discovery, String jobId) {
        try {
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

            discovery.setStatus("done");
            discoveryRepo.save(discovery);

            // 🔥 Save endpoints
            Map<String, Object> paths = (Map<String, Object>) openApi.get("paths");

            for (String path : paths.keySet()) {
                Map<String, Object> ops = (Map<String, Object>) paths.get(path);

                for (String method : ops.keySet()) {
                    if (method.startsWith("x-")) continue;

                    Map<String, Object> op = (Map<String, Object>) ops.get(method);

                    Endpoint endpoint = new Endpoint();
                    endpoint.setDiscovery(discovery);
                    endpoint.setMethod(method.toUpperCase());
                    endpoint.setPath(path);
                    endpoint.setSummary((String) op.get("summary"));
                    endpoint.setSource((String) op.get("x-source"));

                    Object confidenceObj = op.get("x-confidence");
                    endpoint.setConfidence(confidenceObj != null ? ((Number) confidenceObj).doubleValue() : 1.0);

                    if (op.get("x-metadata") != null) {
                        Map<String, Object> metadata = (Map<String, Object>) op.get("x-metadata");
                        endpoint.setRequestSchema(metadata.get("request") != null ? metadata.get("request").toString() : null);
                    }

                    endpointRepo.save(endpoint);
                }
            }

            System.out.println("✅ Discovery completed and endpoints saved!");

        } catch (Exception e) {
            discovery.setStatus("error");
            discovery.setError(e.getMessage());
            discoveryRepo.save(discovery);

            System.out.println("❌ Discovery failed: " + e.getMessage());
        }
    }

}
