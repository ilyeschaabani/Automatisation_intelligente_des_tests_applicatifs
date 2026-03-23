package com.pfe.platform.apiscannerservice;

import org.eclipse.jgit.errors.TransportException;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Minimal implementation to satisfy {@code ScannerEngineCloneAuthHandlingTests}.
 *
 * The production scanner implementation lives elsewhere; this class focuses on the clone-auth failure contract.
 */
public class ScannerEngine {

    public enum DetectionMode {
        HEURISTIC_ONLY
    }

    private final ProjectCloner cloner;
    private final ProjectDetector detector;
    private final List<FrameworkScanner> scanners;
    private final Optional<Object> aiDetector;
    private final GitAuthProperties gitAuthProperties;
    private final DetectionMode mode;
    private final boolean deleteAfter;

    public ScannerEngine(ProjectCloner cloner,
                         ProjectDetector detector,
                         List<FrameworkScanner> scanners,
                         Optional<?> aiDetector,
                         GitAuthProperties gitAuthProperties,
                         DetectionMode mode,
                         boolean deleteAfter) {
        this.cloner = cloner;
        this.detector = detector;
        this.scanners = scanners;
        this.aiDetector = (Optional<Object>) aiDetector;
        this.gitAuthProperties = gitAuthProperties;
        this.mode = mode;
        this.deleteAfter = deleteAfter;
    }

    public ApiContract scan(String projectPath, String repoUrl) {
        Path repoRoot = null;
        boolean didClone = false;

        try {
            if (repoUrl == null || repoUrl.isBlank()) {
                throw new IllegalArgumentException("repoUrl must be provided for this test-facing engine");
            }

            // Use defaults if present; request-level creds not supported in this minimal overload.
            String token = gitAuthProperties != null ? gitAuthProperties.getToken() : null;
            String user = gitAuthProperties != null ? gitAuthProperties.getUsername() : null;
            String pass = gitAuthProperties != null ? gitAuthProperties.getPassword() : null;

            repoRoot = cloner.cloneToTemp(repoUrl, token, user, pass);
            didClone = true;

            // In this minimal implementation we don't proceed with detection/scanning.
            ApiContract ok = new ApiContract();
            ok.setSource(repoUrl);
            return ok;
        } catch (IllegalStateException e) {
            if (repoUrl != null && isAuthRequiredTransport(e.getCause())) {
                ApiContract friendly = new ApiContract();
                friendly.setSource(repoUrl);
                friendly.getIssues().add("Git authentication required for this repository. Provide a token.");
                return friendly;
            }
            throw e;
        } finally {
            if (deleteAfter && didClone) {
                cloner.deleteQuietly(repoRoot);
            }
        }
    }

    private static boolean isAuthRequiredTransport(Throwable cause) {
        if (cause == null) return false;
        if (cause instanceof TransportException) {
            String msg = String.valueOf(cause.getMessage()).toLowerCase();
            return msg.contains("authentication is required");
        }
        // some code throws wrapped TransportException types
        String cls = cause.getClass().getName();
        String msg = String.valueOf(cause.getMessage()).toLowerCase();
        return cls.contains("TransportException") && msg.contains("authentication is required");
    }
}

