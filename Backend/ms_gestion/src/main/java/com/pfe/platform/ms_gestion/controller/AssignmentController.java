package com.pfe.platform.ms_gestion.controller;

import com.pfe.platform.ms_gestion.entity.ExecutionResultRef;
import com.pfe.platform.ms_gestion.entity.SecurityVulnerability;
import com.pfe.platform.ms_gestion.repository.ExecutionResultRefRepository;
import com.pfe.platform.ms_gestion.repository.SecurityVulnerabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final SecurityVulnerabilityRepository vulnRepository;
    private final ExecutionResultRefRepository resultRepository;

    @GetMapping
    public ResponseEntity<?> getAssignments(@RequestParam Long userId) {
        List<SecurityVulnerability> vulns = vulnRepository.findByAssignedToUserIdOrderByCreatedAtDesc(userId);
        List<ExecutionResultRef> results = resultRepository.findByAssignedToUserIdOrderByIdDesc(userId);

        List<Map<String, Object>> vulnList = vulns.stream().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.getId());
            m.put("kind", "vulnerability");
            m.put("title", v.getTitle());
            m.put("severity", v.getSeverity().name());
            m.put("status", v.getStatus().name());
            m.put("vulnType", v.getVulnType().name());
            m.put("cweId", v.getCweId());
            m.put("owaspCategory", v.getOwaspCategory());
            m.put("file", v.getFile());
            m.put("endpoint", v.getEndpoint());
            m.put("assignedTo", v.getAssignedTo());
            m.put("createdAt", v.getCreatedAt());
            m.put("projectId", v.getProject() != null ? v.getProject().getId() : null);
            m.put("projectName", v.getProject() != null ? v.getProject().getName() : null);
            m.put("scanId", v.getScan() != null ? v.getScan().getId() : null);
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> resultList = results.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("kind", "test-error");
            m.put("testCaseId", r.getTestCaseId());
            m.put("campaignId", r.getCampaignId());
            m.put("status", r.getStatus());
            m.put("errorMessage", r.getErrorMessage());
            m.put("assignedTo", r.getAssignedTo());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("vulnerabilities", vulnList);
        response.put("executionResults", resultList);
        return ResponseEntity.ok(response);
    }
}
