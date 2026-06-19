package com.pfe.platform.msexecution.controller;

import com.pfe.platform.msexecution.entity.SecurityScan;
import com.pfe.platform.msexecution.entity.SecurityVulnerability;
import com.pfe.platform.msexecution.service.SecurityScanService;
import com.pfe.platform.msexecution.service.SecurityReportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/security")
@CrossOrigin(origins = "*")
public class SecurityScanController {

    private final SecurityScanService scanService;
    private final SecurityReportService reportService;

    public SecurityScanController(SecurityScanService scanService,
                                  SecurityReportService reportService) {
        this.scanService = scanService;
        this.reportService = reportService;
    }

    @PostMapping("/scan/sast")
    public ResponseEntity<Map<String, Object>> launchSast(
            @RequestParam Long projectId,
            @RequestParam Long environmentId) {
        scanService.runSastScan(projectId, environmentId);
        return ResponseEntity.accepted().body(Map.of(
                "message", "SAST scan launched (Semgrep)",
                "projectId", projectId,
                "environmentId", environmentId
        ));
    }

    @PostMapping("/scan/dast")
    public ResponseEntity<Map<String, Object>> launchDast(
            @RequestParam Long projectId,
            @RequestParam Long environmentId) {
        scanService.runDastScan(projectId, environmentId);
        return ResponseEntity.accepted().body(Map.of(
                "message", "DAST scan launched (OWASP ZAP)",
                "projectId", projectId,
                "environmentId", environmentId
        ));
    }

    @PostMapping("/scan/sca")
    public ResponseEntity<Map<String, Object>> launchSca(
            @RequestParam Long projectId,
            @RequestParam Long environmentId) {
        scanService.runScaScan(projectId, environmentId);
        return ResponseEntity.accepted().body(Map.of(
                "message", "SCA scan launched (OWASP Dependency-Check)",
                "projectId", projectId,
                "environmentId", environmentId
        ));
    }

    @GetMapping("/scans")
    public ResponseEntity<List<SecurityScan>> listScans(@RequestParam Long projectId) {
        return ResponseEntity.ok(scanService.getScansForProject(projectId));
    }

    @GetMapping("/scans/{id}")
    public ResponseEntity<SecurityScan> getScan(@PathVariable Long id) {
        SecurityScan scan = scanService.getScan(id);
        if (scan == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(scan);
    }

    @GetMapping("/scans/ref/{scanRef}")
    public ResponseEntity<SecurityScan> getScanByRef(@PathVariable String scanRef) {
        SecurityScan scan = scanService.getScanByRef(scanRef);
        if (scan == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(scan);
    }

    @GetMapping("/scans/{scanId}/vulnerabilities")
    public ResponseEntity<List<SecurityVulnerability>> getVulnsForScan(@PathVariable Long scanId) {
        return ResponseEntity.ok(scanService.getVulnsForScan(scanId));
    }

    @GetMapping("/report/{scanId}")
    public ResponseEntity<ByteArrayResource> downloadReport(@PathVariable Long scanId) {
        SecurityScan scan = scanService.getScan(scanId);
        if (scan == null) return ResponseEntity.notFound().build();

        List<SecurityVulnerability> vulns = scanService.getVulnsForScan(scanId);
        byte[] pdf = reportService.generateSecurityReport(scan, vulns);

        ByteArrayResource resource = new ByteArrayResource(pdf);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=security-report-" + scan.getScanRef() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(resource);
    }

    @GetMapping("/report/project/{projectId}")
    public ResponseEntity<ByteArrayResource> downloadProjectReport(@PathVariable Long projectId) {
        List<SecurityScan> scans = scanService.getScansForProject(projectId);
        if (scans.isEmpty()) return ResponseEntity.notFound().build();

        byte[] pdf = reportService.generateProjectSecurityReport(projectId, scans);

        ByteArrayResource resource = new ByteArrayResource(pdf);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=security-audit-project-" + projectId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(resource);
    }
}
