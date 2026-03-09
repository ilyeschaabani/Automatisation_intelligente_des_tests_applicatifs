package com.pfe.platform.apiscannerservice.Service;

import com.pfe.platform.apiscannerservice.Model.ApiContract;
import com.pfe.platform.apiscannerservice.Model.ProjectMetadata;
import com.pfe.platform.apiscannerservice.Scanner.FrameworkScanner;
import com.pfe.platform.apiscannerservice.Util.ProjectCloner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ScannerEngine {

    private static final Logger log = LoggerFactory.getLogger(ScannerEngine.class);

    private final ProjectCloner cloner;
    private final ProjectDetector detector;
    private final List<FrameworkScanner> scanners;
    private final Optional<AiFrameworkDetector> aiDetector;

    public ScannerEngine(ProjectCloner cloner, ProjectDetector detector, List<FrameworkScanner> scanners, Optional<AiFrameworkDetector> aiDetector) {
        this.cloner = cloner;
        this.detector = detector;
        this.scanners = scanners;
        this.aiDetector = aiDetector;
    }

    public ApiContract scan(String projectPath, String repoUrl) {
        return scan(projectPath, repoUrl, null, null, null);
    }

    public ApiContract scan(String projectPath, String repoUrl, String gitToken, String gitUsername, String gitPassword) {
        Instant start = Instant.now();
        log.info("scan.start projectPathPresent={} repoUrlPresent={}",
                projectPath != null && !projectPath.isBlank(),
                repoUrl != null && !repoUrl.isBlank());

        if ((projectPath == null || projectPath.isBlank()) && (repoUrl == null || repoUrl.isBlank())) {
            throw new IllegalArgumentException("Either projectPath or repoUrl must be provided");
        }
        if (projectPath != null && !projectPath.isBlank() && repoUrl != null && !repoUrl.isBlank()) {
            throw new IllegalArgumentException("Provide either projectPath or repoUrl, not both");
        }

        Path repoRoot = null;
        boolean deleteAfter = false;
        String source;

        try {
            if (repoUrl != null && !repoUrl.isBlank()) {
                Instant t0 = Instant.now();
                log.info("scan.clone.start repoUrl={}", repoUrl);
                repoRoot = cloner.cloneToTemp(repoUrl, gitToken, gitUsername, gitPassword);
                deleteAfter = true;
                source = repoUrl;
                log.info("scan.clone.done repoRoot={} tookMs={}", repoRoot, Duration.between(t0, Instant.now()).toMillis());
            } else {
                if (projectPath == null || projectPath.isBlank()) {
                    throw new IllegalArgumentException("projectPath must not be blank when repoUrl is not provided");
                }
                repoRoot = Path.of(projectPath).toAbsolutePath().normalize();
                if (!Files.exists(repoRoot) || !Files.isDirectory(repoRoot)) {
                    throw new IllegalArgumentException("projectPath does not exist or is not a directory: " + repoRoot);
                }
                source = repoRoot.toString();
                log.info("scan.localProject repoRoot={}", repoRoot);
            }

            Instant tDetect = Instant.now();
            log.info("scan.detect.start repoRoot={}", repoRoot);
            var detection = detector.detectProject(repoRoot);

            if (detection.getMetadata() != null && detection.getMetadata().getFramework() == ProjectMetadata.Framework.UNKNOWN) {
                if (aiDetector.isPresent()) {
                    Instant tAi = Instant.now();
                    log.info("ai.detect.start repoRoot={}", repoRoot);
                    var ai = aiDetector.get().detectWithAi(repoRoot);
                    log.info("ai.detect.done resultPresent={} tookMs={}", ai != null, Duration.between(tAi, Instant.now()).toMillis());
                    if (ai != null && ai.getMetadata() != null && ai.getMetadata().getFramework() != ProjectMetadata.Framework.UNKNOWN) {
                        detection = ai;
                        detection.getMetadata().getHints().put("detection", "ai");
                    } else {
                        detection.getMetadata().getHints().put("detection", "heuristic");
                    }
                } else {
                    detection.getMetadata().getHints().put("detection", "heuristic");
                }
            } else if (detection.getMetadata() != null) {
                detection.getMetadata().getHints().put("detection", "heuristic");
            }

            Path scanRoot = detection.getProjectRoot();
            ProjectMetadata metadata = detection.getMetadata();
            log.info("scan.detect.done framework={} scanRoot={} tookMs={}",
                    metadata.getFramework(), scanRoot, Duration.between(tDetect, Instant.now()).toMillis());

            Instant tSelect = Instant.now();
            FrameworkScanner scanner = scanners.stream()
                    .filter(s -> s.supports(metadata))
                    .findFirst()
                    .orElse(null);
            log.info("scan.scannerSelect.done selected={} tookMs={}",
                    scanner != null ? scanner.getClass().getSimpleName() : "NONE",
                    Duration.between(tSelect, Instant.now()).toMillis());

            if (scanner == null) {
                ApiContract contract = new ApiContract();
                contract.setSource(source);
                contract.setMetadata(metadata);
                contract.setEndpoints(new ArrayList<>());
                contract.getIssues().add("No scanner found for framework: " + metadata.getFramework());
                log.info("scan.done status=NO_SCANNER totalMs={}", Duration.between(start, Instant.now()).toMillis());
                return contract;
            }

            Instant tScan = Instant.now();
            log.info("scan.frameworkScan.start scanner={} scanRoot={}", scanner.getClass().getSimpleName(), scanRoot);
            ApiContract contract = scanner.scan(scanRoot);
            log.info("scan.frameworkScan.done endpoints={} tookMs={}",
                    contract.getEndpoints() != null ? contract.getEndpoints().size() : 0,
                    Duration.between(tScan, Instant.now()).toMillis());

            contract.setSource(source);
            contract.setMetadata(metadata);
            log.info("scan.done status=OK totalMs={}", Duration.between(start, Instant.now()).toMillis());
            return contract;
        } finally {
            if (deleteAfter) {
                Instant t0 = Instant.now();
                cloner.deleteQuietly(repoRoot);
                log.info("scan.cleanup.done repoRoot={} tookMs={}", repoRoot, Duration.between(t0, Instant.now()).toMillis());
            }
        }
    }
}
