package com.pfe.platform.testmanagementmicroservice.service.DockerComposeRuntime;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerComposeRuntimeService {

    private final RestTemplate restTemplate;

    private final ExecutorService ioPool = Executors.newCachedThreadPool();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @PreDestroy
    public void shutdown() {
        ioPool.shutdownNow();
    }

    public int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to allocate a free host port", ex);
        }
    }

    public String newProjectName(Long executionId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "run_" + executionId + "_" + suffix;
    }

    public void composeUpAndWait(
            Path repoDir,
            Path composeFile,
            String projectName,
            URI healthUri,
            Duration composeTimeout,
            Duration readinessTimeout,
            Duration pollInterval
    ) {
        Objects.requireNonNull(repoDir, "repoDir");
        Objects.requireNonNull(composeFile, "composeFile");
        Objects.requireNonNull(projectName, "projectName");
        Objects.requireNonNull(healthUri, "healthUri");

        runCommand(
                List.of(
                        "docker", "compose",
                        "-p", projectName,
                        "-f", composeFile.toString(),
                        "up", "-d", "--build"
                ),
                repoDir,
                composeTimeout,
                projectName,
                "compose-up"
        );

        waitForApiReady(healthUri, readinessTimeout, pollInterval, projectName);
    }

    public void composeDownAndCleanup(
            Path repoDir,
            Path composeFile,
            String projectName,
            String discoveryBaseUrl,
            String sessionId,
            Duration cleanupTimeout
    ) {
        RuntimeException firstFailure = null;

        try {
            runCommand(
                    List.of(
                            "docker", "compose",
                            "-p", projectName,
                            "-f", composeFile.toString(),
                            "down", "-v"
                    ),
                    repoDir,
                    cleanupTimeout,
                    projectName,
                    "compose-down"
            );
        } catch (RuntimeException ex) {
            firstFailure = ex;
            log.warn("[{}] compose down failed: {}", projectName, ex.getMessage());
        }

        try {
            callCloneDone(discoveryBaseUrl, sessionId);
        } catch (RuntimeException ex) {
            if (firstFailure == null) {
                firstFailure = ex;
            } else {
                firstFailure.addSuppressed(ex);
            }
            log.warn("[{}] clone_repo done failed: {}", projectName, ex.getMessage());
        }

        deleteDirectoryQuietly(repoDir);

        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    public void waitForApiReady(
            URI healthUri,
            Duration timeout,
            Duration pollInterval,
            String runTag
    ) {
        Instant deadline = Instant.now().plus(timeout);
        Exception lastError = null;

        while (Instant.now().isBefore(deadline)) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(healthUri)
                        .GET()
                        .timeout(Duration.ofSeconds(5))
                        .build();

                HttpResponse<Void> response = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    log.info("[{}] API is ready at {}", runTag, healthUri);
                    return;
                }

                log.info("[{}] API not ready yet, status={}", runTag, status);
            } catch (Exception ex) {
                lastError = ex;
                log.info("[{}] Health check failed: {}", runTag, ex.getMessage());
            }

            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for API readiness", ex);
            }
        }

        String message = "Timed out waiting for API readiness at " + healthUri;
        if (lastError != null) {
            message += ". Last error: " + lastError.getMessage();
        }
        throw new IllegalStateException(message);
    }

    private void callCloneDone(String discoveryBaseUrl, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;

        String base = discoveryBaseUrl == null ? "" : discoveryBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isBlank()) {
            throw new IllegalStateException("discovery.base-url is empty");
        }

        String url = base + "/done/" + UriUtils.encodePathSegment(sessionId, StandardCharsets.UTF_8);
        restTemplate.postForEntity(url, HttpEntity.EMPTY, Map.class);
    }

    private String runCommand(
            List<String> command,
            Path workingDirectory,
            Duration timeout,
            String runTag,
            String phase
    ) {
        ProcessBuilder pb = new ProcessBuilder(new ArrayList<>(command));
        pb.directory(workingDirectory.toFile());
        pb.redirectErrorStream(false);

        Process process;
        try {
            process = pb.start();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start command: " + String.join(" ", command), ex);
        }

        StringBuilder tail = new StringBuilder();

        Future<?> outFuture = streamLinesAsync(process.getInputStream(), runTag, phase, "OUT", tail);
        Future<?> errFuture = streamLinesAsync(process.getErrorStream(), runTag, phase, "ERR", tail);

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while executing: " + String.join(" ", command), ex);
        }

        if (!finished) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException(
                    "Command timed out during " + phase + ": " + String.join(" ", command) + ". Recent logs: " + tail
            );
        }

        waitStream(outFuture);
        waitStream(errFuture);

        int exit = process.exitValue();
        if (exit != 0) {
            throw new IllegalStateException(
                    "Command failed with exit code " + exit + " during " + phase + ": "
                            + String.join(" ", command) + ". Recent logs: " + tail
            );
        }

        return tail.toString();
    }

    private Future<?> streamLinesAsync(
            InputStream inputStream,
            String runTag,
            String phase,
            String streamName,
            StringBuilder tail
    ) {
        return ioPool.submit(() -> streamLines(inputStream, runTag, phase, streamName, tail));
    }

    private void streamLines(
            InputStream inputStream,
            String runTag,
            String phase,
            String streamName,
            StringBuilder tail
    ) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[{}][{}][{}] {}", runTag, phase, streamName, line);
                appendTail(tail, line);
            }
        } catch (IOException ex) {
            log.warn("[{}][{}][{}] log stream error: {}", runTag, phase, streamName, ex.getMessage());
        }
    }

    private synchronized void appendTail(StringBuilder tail, String line) {
        tail.append(line).append(" | ");
        int max = 4000;
        if (tail.length() > max) {
            tail.delete(0, tail.length() - max);
        }
    }

    private void waitStream(Future<?> future) {
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private void deleteDirectoryQuietly(Path root) {
        if (root == null || !Files.exists(root)) return;

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ex) {
            log.warn("Failed deleting temp folder {}: {}", root, ex.getMessage());
        }
    }
}