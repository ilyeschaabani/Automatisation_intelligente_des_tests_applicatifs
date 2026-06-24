package com.pfe.platform.ms_gestion.service;

import com.pfe.platform.ms_gestion.entity.ComplianceResult;
import com.pfe.platform.ms_gestion.entity.SecurityScan;
import com.pfe.platform.ms_gestion.entity.SecurityVulnerability;
import com.pfe.platform.ms_gestion.repository.ComplianceResultRepository;
import com.pfe.platform.ms_gestion.repository.SecurityScanRepository;
import com.pfe.platform.ms_gestion.repository.SecurityVulnerabilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityService {

    private final SecurityScanRepository scanRepository;
    private final SecurityVulnerabilityRepository vulnRepository;
    private final ComplianceResultRepository complianceRepository;

    // ── SCANS ──────────────────────────────────────────────

    public List<SecurityScan> getAllScans() {
        return scanRepository.findAllOrderByDate();
    }

    public List<SecurityScan> getScansByProject(Long projectId) {
        return scanRepository.findByProjectIdOrderByStartedAtDesc(projectId);
    }

    public List<SecurityScan> getScansByType(String type) {
        SecurityScan.ScanType scanType = SecurityScan.ScanType.valueOf(type.toUpperCase());
        return scanRepository.findByScanTypeOrderByStartedAtDesc(scanType);
    }

    public List<SecurityScan> getScansByProjectAndType(Long projectId, String type) {
        SecurityScan.ScanType scanType = SecurityScan.ScanType.valueOf(type.toUpperCase());
        return scanRepository.findByProjectIdAndScanTypeOrderByStartedAtDesc(projectId, scanType);
    }

    public Optional<SecurityScan> getScanById(Long id) {
        return scanRepository.findById(id);
    }

    @Transactional
    public SecurityScan saveScan(SecurityScan scan) {
        return scanRepository.save(scan);
    }

    // ── VULNERABILITIES ────────────────────────────────────

    public List<SecurityVulnerability> getAllVulnerabilities() {
        return vulnRepository.findAllOrderByDate();
    }

    public List<SecurityVulnerability> getVulnerabilitiesByProject(Long projectId) {
        return vulnRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public List<SecurityVulnerability> getVulnerabilitiesByScan(Long scanId) {
        return vulnRepository.findByScanIdOrderBySeverityAsc(scanId);
    }

    public Optional<SecurityVulnerability> getVulnerabilityById(Long id) {
        return vulnRepository.findById(id);
    }

    @Transactional
    public SecurityVulnerability updateVulnerabilityStatus(Long id, String status) {
        SecurityVulnerability vuln = vulnRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vulnerability not found: " + id));
        vuln.setStatus(SecurityVulnerability.VulnStatus.valueOf(status.toUpperCase()));
        vuln.setUpdatedAt(java.time.LocalDateTime.now());
        return vulnRepository.save(vuln);
    }

    @Transactional
    public SecurityVulnerability assignVulnerability(Long id, String assignee) {
        SecurityVulnerability vuln = vulnRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vulnerability not found: " + id));
        vuln.setAssignedTo(assignee);
        vuln.setUpdatedAt(java.time.LocalDateTime.now());
        return vulnRepository.save(vuln);
    }

    // ── COMPLIANCE ─────────────────────────────────────────

    public List<ComplianceResult> getAllCompliance() {
        return complianceRepository.findAllByOrderByEvaluatedAtDesc();
    }

    public List<ComplianceResult> getComplianceByProject(Long projectId) {
        return complianceRepository.findByProjectIdOrderByEvaluatedAtDesc(projectId);
    }

    // ── DASHBOARD / KPIs ───────────────────────────────────

    public Map<String, Object> getDashboard(Long projectId) {
        Map<String, Object> dashboard = new LinkedHashMap<>();

        List<SecurityVulnerability> vulns = projectId != null
                ? vulnRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                : vulnRepository.findAllOrderByDate();

        long total = vulns.size();
        long critical = vulns.stream().filter(v -> v.getSeverity() == SecurityVulnerability.Severity.CRITICAL).count();
        long high = vulns.stream().filter(v -> v.getSeverity() == SecurityVulnerability.Severity.HIGH).count();
        long medium = vulns.stream().filter(v -> v.getSeverity() == SecurityVulnerability.Severity.MEDIUM).count();
        long low = vulns.stream().filter(v -> v.getSeverity() == SecurityVulnerability.Severity.LOW).count();
        long open = vulns.stream().filter(v -> v.getStatus() == SecurityVulnerability.VulnStatus.OPEN).count();
        long inProgress = vulns.stream().filter(v -> v.getStatus() == SecurityVulnerability.VulnStatus.IN_PROGRESS).count();
        long resolved = vulns.stream().filter(v -> v.getStatus() == SecurityVulnerability.VulnStatus.RESOLVED
                || v.getStatus() == SecurityVulnerability.VulnStatus.CLOSED).count();

        int securityScore = total == 0 ? 100
                : Math.max(0, (int)(100 - (critical * 15 + high * 12 + medium * 4 + low * 1)));

        Map<String, Long> severityCounts = new LinkedHashMap<>();
        severityCounts.put("critical", critical);
        severityCounts.put("high", high);
        severityCounts.put("medium", medium);
        severityCounts.put("low", low);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        statusCounts.put("open", open);
        statusCounts.put("in_progress", inProgress);
        statusCounts.put("resolved", resolved);

        dashboard.put("totalVulnerabilities", total);
        dashboard.put("severityCounts", severityCounts);
        dashboard.put("statusCounts", statusCounts);
        dashboard.put("securityScore", securityScore);

        // Sous-scores RÉELS dérivés des vulnérabilités par type (formule pondérée par sévérité).
        long owaspCategoriesHit = vulns.stream()
                .filter(v -> v.getOwaspCategory() != null)
                .map(v -> v.getOwaspCategory().replaceAll("(?i)^(A\\d{2}).*", "$1"))
                .filter(s -> s.matches("(?i)A\\d{2}"))
                .distinct().count();

        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("overall", securityScore);
        scores.put("owasp", (int) ((10 - Math.min(10, owaspCategoriesHit)) * 10));
        scores.put("secureCoding", weightedScore(vulns, SecurityVulnerability.VulnType.SAST));
        scores.put("infrastructure", weightedScore(vulns, SecurityVulnerability.VulnType.DAST));
        scores.put("dependencies", weightedScore(vulns, SecurityVulnerability.VulnType.SCA));
        dashboard.put("scores", scores);

        Map<String, Object> owaspTop10 = buildOwaspTop10(vulns);
        dashboard.put("owaspTop10", owaspTop10);

        List<SecurityScan> scans = projectId != null
                ? scanRepository.findByProjectIdOrderByStartedAtDesc(projectId)
                : scanRepository.findAllOrderByDate();
        dashboard.put("recentScans", scans.stream().limit(5).collect(Collectors.toList()));
        dashboard.put("totalScans", scans.size());

        List<ComplianceResult> compliance = projectId != null
                ? complianceRepository.findByProjectIdOrderByEvaluatedAtDesc(projectId)
                : complianceRepository.findAllByOrderByEvaluatedAtDesc();
        dashboard.put("compliance", compliance);

        return dashboard;
    }

    /** Score pondéré par sévérité pour un type de scan : 100 = zéro finding. */
    private int weightedScore(List<SecurityVulnerability> vulns, SecurityVulnerability.VulnType type) {
        long c = vulns.stream().filter(v -> v.getVulnType() == type && v.getSeverity() == SecurityVulnerability.Severity.CRITICAL).count();
        long h = vulns.stream().filter(v -> v.getVulnType() == type && v.getSeverity() == SecurityVulnerability.Severity.HIGH).count();
        long m = vulns.stream().filter(v -> v.getVulnType() == type && v.getSeverity() == SecurityVulnerability.Severity.MEDIUM).count();
        long l = vulns.stream().filter(v -> v.getVulnType() == type && v.getSeverity() == SecurityVulnerability.Severity.LOW).count();
        return Math.max(0, (int) (100 - (c * 15 + h * 12 + m * 4 + l * 1)));
    }

    private Map<String, Object> buildOwaspTop10(List<SecurityVulnerability> vulns) {
        String[][] categories = {
                {"A01", "Broken Access Control"},
                {"A02", "Cryptographic Failures"},
                {"A03", "Injection"},
                {"A04", "Insecure Design"},
                {"A05", "Security Misconfiguration"},
                {"A06", "Vulnerable & Outdated Components"},
                {"A07", "Identification & Authentication Failures"},
                {"A08", "Software & Data Integrity Failures"},
                {"A09", "Security Logging & Monitoring Failures"},
                {"A10", "Server-Side Request Forgery"},
        };

        List<Map<String, Object>> result = new ArrayList<>();
        for (String[] cat : categories) {
            String catId = cat[0];
            String catName = cat[1];
            long count = vulns.stream()
                    .filter(v -> v.getOwaspCategory() != null && v.getOwaspCategory().startsWith(catId))
                    .count();
            int riskScore = count == 0 ? 95 : Math.max(30, (int)(95 - count * 12));
            String status = count == 0 ? "pass" : count <= 2 ? "warn" : "fail";

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", catId);
            entry.put("name", catName);
            entry.put("findingsCount", count);
            entry.put("riskScore", riskScore);
            entry.put("status", status);
            result.add(entry);
        }
        return Map.of("categories", result);
    }
}
