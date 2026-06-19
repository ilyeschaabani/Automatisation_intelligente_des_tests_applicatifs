package com.pfe.platform.msexecution.service;

import com.pfe.platform.msexecution.entity.*;
import com.pfe.platform.msexecution.repository.*;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class SecurityScanService {

    private static final Logger log = LoggerFactory.getLogger(SecurityScanService.class);

    private final SecurityScanRepository scanRepo;
    private final SecurityVulnerabilityRepository vulnRepo;
    private final ProjectRepository projectRepo;
    private final EnvironmentRepository envRepo;
    private final SemgrepScanService semgrepService;
    private final ZapScanService zapService;
    private final DependencyCheckService depCheckService;

    @Value("${github.token:}")
    private String githubToken;

    public SecurityScanService(SecurityScanRepository scanRepo,
                               SecurityVulnerabilityRepository vulnRepo,
                               ProjectRepository projectRepo,
                               EnvironmentRepository envRepo,
                               SemgrepScanService semgrepService,
                               ZapScanService zapService,
                               DependencyCheckService depCheckService) {
        this.scanRepo = scanRepo;
        this.vulnRepo = vulnRepo;
        this.projectRepo = projectRepo;
        this.envRepo = envRepo;
        this.semgrepService = semgrepService;
        this.zapService = zapService;
        this.depCheckService = depCheckService;
    }

    @Async
    public void runSastScan(Long projectId, Long environmentId) {
        SecurityScan scan = initScan(projectId, SecurityScan.ScanType.SAST, "Semgrep");
        Path repoDir = null;
        try {
            Environment env = envRepo.findById(environmentId)
                    .orElseThrow(() -> new RuntimeException("Environment not found: " + environmentId));

            scan.setBranch(env.getGitBranch());
            scanRepo.save(scan);

            repoDir = cloneRepository(env.getGitRepoUrl(), env.getGitBranch());

            String commitHash = resolveCommitHash(repoDir);
            scan.setCommitHash(commitHash);

            int files = semgrepService.countJavaFiles(repoDir);
            int lines = semgrepService.countLinesOfCode(repoDir);
            scan.setFilesAnalyzed(files);
            scan.setLinesAnalyzed(lines);

            List<SecurityVulnerability> findings = semgrepService.scan(repoDir);
            completeScan(scan, findings, projectId);

        } catch (Exception e) {
            failScan(scan, e);
        } finally {
            cleanupDir(repoDir);
        }
    }

    @Async
    public void runDastScan(Long projectId, Long environmentId) {
        SecurityScan scan = initScan(projectId, SecurityScan.ScanType.DAST, "OWASP ZAP");
        try {
            Environment env = envRepo.findById(environmentId)
                    .orElseThrow(() -> new RuntimeException("Environment not found: " + environmentId));

            String targetUrl = env.getBaseUrlApi();
            if (targetUrl == null || targetUrl.isBlank()) {
                targetUrl = env.getBaseUrlWeb();
            }
            if (targetUrl == null || targetUrl.isBlank()) {
                throw new RuntimeException("No target URL configured in environment " + environmentId);
            }

            scan.setTargetUrl(targetUrl);
            scanRepo.save(scan);

            List<SecurityVulnerability> findings = zapService.scan(targetUrl);
            completeScan(scan, findings, projectId);

        } catch (Exception e) {
            failScan(scan, e);
        }
    }

    @Async
    public void runScaScan(Long projectId, Long environmentId) {
        SecurityScan scan = initScan(projectId, SecurityScan.ScanType.SCA, "OWASP Dependency-Check");
        Path repoDir = null;
        try {
            Environment env = envRepo.findById(environmentId)
                    .orElseThrow(() -> new RuntimeException("Environment not found: " + environmentId));

            scan.setBranch(env.getGitBranch());
            scanRepo.save(scan);

            repoDir = cloneRepository(env.getGitRepoUrl(), env.getGitBranch());

            String commitHash = resolveCommitHash(repoDir);
            scan.setCommitHash(commitHash);

            List<SecurityVulnerability> findings = depCheckService.scan(repoDir);
            completeScan(scan, findings, projectId);

        } catch (Exception e) {
            failScan(scan, e);
        } finally {
            cleanupDir(repoDir);
        }
    }

    public SecurityScan getScan(Long scanId) {
        return scanRepo.findById(scanId).orElse(null);
    }

    public SecurityScan getScanByRef(String scanRef) {
        return scanRepo.findByScanRef(scanRef).orElse(null);
    }

    public List<SecurityScan> getScansForProject(Long projectId) {
        return scanRepo.findByProjectIdOrderByStartedAtDesc(projectId);
    }

    public List<SecurityVulnerability> getVulnsForScan(Long scanId) {
        return vulnRepo.findByScanId(scanId);
    }

    // ── Internal helpers ──────────────────────────

    private SecurityScan initScan(Long projectId, SecurityScan.ScanType type, String engine) {
        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        SecurityScan scan = new SecurityScan();
        scan.setScanRef("SCAN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        scan.setScanType(type);
        scan.setEngine(engine);
        scan.setStatus(SecurityScan.ScanStatus.RUNNING);
        scan.setStartedAt(LocalDateTime.now());
        scan.setProject(project);
        return scanRepo.save(scan);
    }

    private void completeScan(SecurityScan scan, List<SecurityVulnerability> findings, Long projectId) {
        scan.setStatus(SecurityScan.ScanStatus.COMPLETED);
        scan.setCompletedAt(LocalDateTime.now());
        scan.setDuration(formatDuration(scan.getStartedAt(), scan.getCompletedAt()));

        if (scan.getScanType() == SecurityScan.ScanType.SAST && findings.size() > 0) {
            int totalLines = scan.getLinesAnalyzed() != null ? scan.getLinesAnalyzed() : 1;
            double defectDensity = (findings.size() * 1000.0) / Math.max(totalLines, 1);
            scan.setCoverage(Math.max(0, 100 - defectDensity));
        }

        scanRepo.save(scan);

        Project project = scan.getProject();
        LocalDateTime now = LocalDateTime.now();
        for (SecurityVulnerability v : findings) {
            v.setScan(scan);
            v.setProject(project);
            v.setCreatedAt(now);
            v.setUpdatedAt(now);
        }
        vulnRepo.saveAll(findings);

        log.info("[Security] Scan {} completed: {} findings, duration {}",
                scan.getScanRef(), findings.size(), scan.getDuration());
    }

    private void failScan(SecurityScan scan, Exception e) {
        log.error("[Security] Scan {} failed: {}", scan.getScanRef(), e.getMessage(), e);
        scan.setStatus(SecurityScan.ScanStatus.FAILED);
        scan.setCompletedAt(LocalDateTime.now());
        scan.setDuration(formatDuration(scan.getStartedAt(), scan.getCompletedAt()));
        scanRepo.save(scan);
    }

    private Path cloneRepository(String repoUrl, String branch) throws Exception {
        Path dir = Files.createTempDirectory("security-scan-");
        String effectiveUrl = (githubToken != null && !githubToken.isBlank() && repoUrl.startsWith("https://"))
                ? repoUrl.replace("https://", "https://oauth2:" + githubToken + "@")
                : repoUrl;
        log.info("[Security] Cloning {} @ {} into {}", repoUrl, branch, dir);
        Git.cloneRepository()
                .setURI(effectiveUrl)
                .setDirectory(dir.toFile())
                .setBranch(branch)
                .call()
                .close();
        return dir;
    }

    private String resolveCommitHash(Path repoDir) {
        try (Git git = Git.open(repoDir.toFile())) {
            return git.log().setMaxCount(1).call().iterator().next().getName().substring(0, 8);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void cleanupDir(Path dir) {
        if (dir == null) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
        } catch (Exception ignored) {}
    }

    private String formatDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return "0s";
        Duration d = Duration.between(start, end);
        long minutes = d.toMinutes();
        long seconds = d.getSeconds() % 60;
        return minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
    }
}
