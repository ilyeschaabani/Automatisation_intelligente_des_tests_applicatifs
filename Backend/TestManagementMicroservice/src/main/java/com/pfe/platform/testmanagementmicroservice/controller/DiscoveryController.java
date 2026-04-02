package com.pfe.platform.testmanagementmicroservice.controller;

import com.pfe.platform.testmanagementmicroservice.service.FastAPI.DiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {
    private final DiscoveryService discoveryService;

    public DiscoveryController(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, Object>> startDiscovery(@RequestBody Map<String, String> body) {
        String repoUrl = body.get("repoUrl");
        String branch = body.get("branch");

        if (repoUrl == null || repoUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "repoUrl is required"));
        }

        Map<String, Object> result = discoveryService.startDiscovery(repoUrl, branch);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable String jobId) {
        Map<String, Object> result = discoveryService.getJobStatus(jobId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/jobs/{jobId}/openapi")
    public ResponseEntity<Map<String, Object>> getJobOpenApi(@PathVariable String jobId) {
        Map<String, Object> result = discoveryService.getJobOpenApi(jobId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/jobs/{jobId}/contract/questionnaire")
    public ResponseEntity<Map<String, Object>> getContractQuestionnaire(@PathVariable String jobId) {
        Map<String, Object> result = discoveryService.getContractQuestionnaire(jobId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/jobs/{jobId}/complete")
    public ResponseEntity<Map<String, Object>> completeDiscovery(
            @PathVariable String jobId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        Map<String, Object> result = discoveryService.completeDiscovery(jobId, body != null ? body : new HashMap<>());
        return ResponseEntity.ok(result);
    }
}
