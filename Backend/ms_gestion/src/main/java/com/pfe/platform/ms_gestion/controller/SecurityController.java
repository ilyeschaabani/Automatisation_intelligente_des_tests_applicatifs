package com.pfe.platform.ms_gestion.controller;

import com.pfe.platform.ms_gestion.entity.ComplianceResult;
import com.pfe.platform.ms_gestion.entity.SecurityScan;
import com.pfe.platform.ms_gestion.entity.SecurityVulnerability;
import com.pfe.platform.ms_gestion.service.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class SecurityController {

    private final SecurityService securityService;

    // ── DASHBOARD ──────────────────────────────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(
            @RequestParam(required = false) Long projectId) {
        return ResponseEntity.ok(securityService.getDashboard(projectId));
    }

    // ── SCANS ──────────────────────────────────────────────

    @GetMapping("/scans")
    public ResponseEntity<List<SecurityScan>> getScans(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String type) {
        if (projectId != null && type != null) {
            return ResponseEntity.ok(securityService.getScansByProjectAndType(projectId, type));
        } else if (projectId != null) {
            return ResponseEntity.ok(securityService.getScansByProject(projectId));
        } else if (type != null) {
            return ResponseEntity.ok(securityService.getScansByType(type));
        }
        return ResponseEntity.ok(securityService.getAllScans());
    }

    @GetMapping("/scans/{id}")
    public ResponseEntity<SecurityScan> getScan(@PathVariable Long id) {
        return securityService.getScanById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/scans/{id}/vulnerabilities")
    public ResponseEntity<List<SecurityVulnerability>> getScanVulnerabilities(@PathVariable Long id) {
        return ResponseEntity.ok(securityService.getVulnerabilitiesByScan(id));
    }

    // ── VULNERABILITIES ────────────────────────────────────

    @GetMapping("/vulnerabilities")
    public ResponseEntity<List<SecurityVulnerability>> getVulnerabilities(
            @RequestParam(required = false) Long projectId) {
        if (projectId != null) {
            return ResponseEntity.ok(securityService.getVulnerabilitiesByProject(projectId));
        }
        return ResponseEntity.ok(securityService.getAllVulnerabilities());
    }

    @GetMapping("/vulnerabilities/{id}")
    public ResponseEntity<SecurityVulnerability> getVulnerability(@PathVariable Long id) {
        return securityService.getVulnerabilityById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/vulnerabilities/{id}/details")
    public ResponseEntity<Map<String, Object>> getVulnerabilityDetails(@PathVariable Long id) {
        return securityService.getVulnerabilityById(id)
                .map(v -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", v.getId());
                    result.put("title", v.getTitle());
                    result.put("severity", v.getSeverity().name());
                    result.put("status", v.getStatus().name());
                    result.put("vulnType", v.getVulnType().name());
                    result.put("source", v.getSource());
                    result.put("cweId", v.getCweId());
                    result.put("owaspCategory", v.getOwaspCategory());
                    result.put("file", v.getFile());
                    result.put("line", v.getLine());
                    result.put("snippet", v.getSnippet());
                    result.put("endpoint", v.getEndpoint());
                    result.put("httpMethod", v.getHttpMethod());
                    result.put("parameter", v.getParameter());
                    result.put("description", v.getDescription());
                    result.put("risk", v.getRisk());
                    result.put("recommendation", v.getRecommendation());
                    result.put("fixExample", v.getFixExample());
                    result.put("evidence", v.getEvidence());
                    result.put("assignedTo", v.getAssignedTo());
                    result.put("assignedToUserId", v.getAssignedToUserId());
                    result.put("createdAt", v.getCreatedAt());
                    result.put("updatedAt", v.getUpdatedAt());

                    if (v.getProject() != null) {
                        Map<String, Object> project = new LinkedHashMap<>();
                        project.put("id", v.getProject().getId());
                        project.put("name", v.getProject().getName());
                        project.put("description", v.getProject().getDescription());
                        result.put("project", project);
                    }

                    if (v.getScan() != null) {
                        Map<String, Object> scan = new LinkedHashMap<>();
                        scan.put("id", v.getScan().getId());
                        scan.put("scanRef", v.getScan().getScanRef());
                        scan.put("scanType", v.getScan().getScanType().name());
                        scan.put("engine", v.getScan().getEngine());
                        scan.put("status", v.getScan().getStatus().name());
                        scan.put("startedAt", v.getScan().getStartedAt());
                        scan.put("completedAt", v.getScan().getCompletedAt());
                        scan.put("duration", v.getScan().getDuration());
                        scan.put("branch", v.getScan().getBranch());
                        scan.put("commitHash", v.getScan().getCommitHash());
                        scan.put("coverage", v.getScan().getCoverage());
                        result.put("scan", scan);
                    }

                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/vulnerabilities/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'TESTEUR')")
    public ResponseEntity<SecurityVulnerability> updateVulnerabilityStatus(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(securityService.updateVulnerabilityStatus(id, status));
    }

    @PatchMapping("/vulnerabilities/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER')")
    public ResponseEntity<SecurityVulnerability> assignVulnerability(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object userIdObj = body.get("userId");
        if (userIdObj != null) {
            Long userId = Long.valueOf(userIdObj.toString());
            String name = body.getOrDefault("name", "").toString();
            String email = body.getOrDefault("email", "").toString();
            return ResponseEntity.ok(securityService.assignVulnerability(id, userId, name, email));
        }
        String assignee = (String) body.get("assignedTo");
        if (assignee == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(securityService.assignVulnerabilityLegacy(id, assignee));
    }

    // ── COMPLIANCE ─────────────────────────────────────────

    @GetMapping("/compliance")
    public ResponseEntity<List<ComplianceResult>> getCompliance(
            @RequestParam(required = false) Long projectId) {
        if (projectId != null) {
            return ResponseEntity.ok(securityService.getComplianceByProject(projectId));
        }
        return ResponseEntity.ok(securityService.getAllCompliance());
    }
}
