package com.pfe.platform.ms_gestion.controller;

import com.pfe.platform.ms_gestion.entity.ComplianceResult;
import com.pfe.platform.ms_gestion.entity.SecurityScan;
import com.pfe.platform.ms_gestion.entity.SecurityVulnerability;
import com.pfe.platform.ms_gestion.service.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @PatchMapping("/vulnerabilities/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER')")
    public ResponseEntity<SecurityVulnerability> updateVulnerabilityStatus(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(securityService.updateVulnerabilityStatus(id, status));
    }

    @PatchMapping("/vulnerabilities/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER')")
    public ResponseEntity<SecurityVulnerability> assignVulnerability(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        String assignee = body.get("assignedTo");
        if (assignee == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(securityService.assignVulnerability(id, assignee));
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
