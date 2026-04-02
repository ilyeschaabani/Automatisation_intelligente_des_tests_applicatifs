package com.pfe.platform.testmanagementmicroservice.service.FastAPI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class FastApiClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public FastApiClient(
            RestTemplate restTemplate,
            @Value("${discovery.base-url}") String baseUrl
    ) {
        this.restTemplate = restTemplate;
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.baseUrl = normalized;
    }

    public Map<String, Object> startDiscovery(String repoUrl, String branch) {
        Map<String, Object> body = new HashMap<>();
        body.put("repo_url", repoUrl);
        if (branch != null && !branch.isBlank()) {
            body.put("branch", branch);
        }
        body.put("keep_repo", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        // POST request to FastAPI
        return restTemplate.postForObject(baseUrl + "/discovery/jobs", request, Map.class);
    }

    public Map<String, Object> getJobStatus(String jobId) {
        return restTemplate.getForObject(baseUrl + "/discovery/jobs/{jobId}", Map.class, jobId);
    }

    public Map<String, Object> getJobOpenApi(String jobId) {
        return restTemplate.getForObject(baseUrl + "/discovery/jobs/{jobId}/openapi", Map.class, jobId);
    }
    public Map<String, Object> getJobendpoint(String jobId) {
        return restTemplate.getForObject(baseUrl + "/discovery/jobs/{jobId}/endpoints", Map.class, jobId);
    }

    public Map<String, Object> getContractQuestionnaire(String jobId) {
        return restTemplate.getForObject(
                baseUrl + "/discovery/jobs/{jobId}/contract/questionnaire",
                Map.class,
                jobId
        );
    }

    public Map<String, Object> completeDiscovery(String jobId, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        return restTemplate.postForObject(
                baseUrl + "/discovery/jobs/{jobId}/complete",
                request,
                Map.class,
                jobId
        );
    }
}