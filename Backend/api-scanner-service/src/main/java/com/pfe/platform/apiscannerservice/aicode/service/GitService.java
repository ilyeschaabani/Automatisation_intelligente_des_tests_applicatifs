package com.pfe.platform.apiscannerservice.aicode.service;

import com.pfe.platform.apiscannerservice.aicode.config.AiCodeAnalyzerProperties;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    private static final int DELETE_RETRIES = 4;
    private static final Duration DELETE_RETRY_DELAY = Duration.ofMillis(250);

    private final AiCodeAnalyzerProperties props;

    public GitService(AiCodeAnalyzerProperties props) {
        this.props = props;
    }

    /**
     * Clones into a unique temp directory: {gitTempRoot}/repos/{uuid}/
     *
     * SECURITY: token is never logged, stored, or returned.
     */
    public Path cloneToTemp(String repoUrl, String gitToken) {
        try {
            Path root = Path.of(props.getGitTempRoot()).toAbsolutePath().normalize();
            Path dir = root.resolve("repos").resolve(UUID.randomUUID().toString());
            Files.createDirectories(dir);

            log.info("aicode.clone.start repoUrl={} targetDir={}", repoUrl, dir);

            CloneCommand cmd = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(dir.toFile())
                    .setCloneAllBranches(false);

            CredentialsProvider credentials = buildCredentialsProvider(gitToken);
            if (credentials != null) {
                cmd.setCredentialsProvider(credentials);
            }

            try (Git ignored = cmd.call()) {
                log.info("aicode.clone.done targetDir={}", dir);
                return dir;
            }
        } catch (Exception e) {
            // avoid including token in the exception message; repoUrl is safe.
            throw new IllegalStateException("Failed to clone repository: " + repoUrl, e);
        }
    }

    /**
     * Best-effort recursive delete.
     *
     * Windows note: JGit pack/index files under .git/objects/pack may remain locked briefly.
     * We delete .git first and retry a few times.
     */
    public void deleteRepoQuietly(Path repoDir) {
        if (repoDir == null) return;
        if (!Files.exists(repoDir)) return;

        Path gitDir = repoDir.resolve(".git");

        List<Path> failed = new ArrayList<>();
        boolean success = false;

        for (int attempt = 1; attempt <= DELETE_RETRIES; attempt++) {
            failed.clear();
            try {
                // Delete .git first to release packed objects (common lock source)
                if (Files.exists(gitDir)) {
                    deleteTree(gitDir, failed);
                }
                deleteTree(repoDir, failed);

                if (failed.isEmpty() && !Files.exists(repoDir)) {
                    success = true;
                    break;
                }
            } catch (Exception e) {
                log.debug("aicode.cleanup.attemptFailed repoDir={} attempt={} err={}", repoDir, attempt, e.getMessage());
            }

            if (attempt < DELETE_RETRIES) {
                sleep(DELETE_RETRY_DELAY);
            }
        }

        if (success) {
            log.info("aicode.cleanup.done repoDir={}", repoDir);
        } else {
            // Log remaining locked/unremovable paths
            if (!failed.isEmpty()) {
                for (Path p : failed) {
                    log.warn("aicode.cleanup.cannotDelete repoDir={} path={}", repoDir, p);
                }
            }
            log.warn("aicode.cleanup.failed repoDir={} existsAfter={} remainingFailed={}", repoDir, Files.exists(repoDir), failed.size());
        }
    }

    private void deleteTree(Path root, List<Path> failed) throws IOException {
        if (!Files.exists(root)) return;

        // Use walk so we can: (a) control ordering, (b) log exactly what fails.
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    // On Windows, read-only attributes can sometimes block deletes.
                    try {
                        Files.setAttribute(p, "dos:readonly", false);
                    } catch (Exception ignored) {
                    }
                    Files.deleteIfExists(p);
                } catch (Exception ex) {
                    failed.add(p);
                }
            });
        }
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private CredentialsProvider buildCredentialsProvider(String gitToken) {
        if (gitToken != null && !gitToken.isBlank()) {
            // GitHub PAT over HTTPS: any non-empty username, token as password.
            return new UsernamePasswordCredentialsProvider("token", gitToken);
        }
        return null;
    }
}
