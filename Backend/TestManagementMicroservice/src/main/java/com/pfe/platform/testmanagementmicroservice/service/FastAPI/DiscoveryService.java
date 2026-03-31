package com.pfe.platform.testmanagementmicroservice.service.FastAPI;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DiscoveryService {
    private final FastApiClient fastApiClient;

    public DiscoveryService(FastApiClient fastApiClient) {
        this.fastApiClient = fastApiClient;
    }

    public Map<String, Object> startDiscovery(String repoUrl, String branch) {
        return fastApiClient.startDiscovery(repoUrl, branch);
    }

    public Map<String, Object> getJobStatus(String jobId) {
        return fastApiClient.getJobStatus(jobId);
    }

    public Map<String, Object> getJobOpenApi(String jobId) {
        return fastApiClient.getJobOpenApi(jobId);
    }
}
