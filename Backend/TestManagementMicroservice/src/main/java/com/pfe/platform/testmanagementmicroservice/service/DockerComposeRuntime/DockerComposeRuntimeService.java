package com.pfe.platform.testmanagementmicroservice.service.DockerComposeRuntime;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;
import org.yaml.snakeyaml.Yaml;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerComposeRuntimeService {

    private final RestTemplate restTemplate;

    @Value("${run.repair.base-url:${discovery.base-url}}")
    private String repairBaseUrl;

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

        public URI composeUpAndWait(
            Path repoDir,
            Path composeFile,
            String projectName,
            URI healthUri,
            String apiService,
            Duration composeTimeout,
            Duration readinessTimeout,
            Duration pollInterval
    ) {
        Objects.requireNonNull(repoDir, "repoDir");
        Objects.requireNonNull(composeFile, "composeFile");
        Objects.requireNonNull(projectName, "projectName");
        Objects.requireNonNull(healthUri, "healthUri");

        // Try to build/up; on deterministic failures (e.g. missing/deprecated base images),
        // ask the clone_repo service to patch Dockerfiles in the temp checkout and retry once.
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
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
                lastFailure = null;
                break;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (attempt == 1 && tryRepairDockerfiles(repoDir, composeFile, projectName, ex)) {
                    log.info("[{}] Dockerfile repair applied; retrying docker compose up", projectName);
                    continue;
                }
                throw ex;
            }
        }

        URI effectiveHealthUri = resolveEffectiveHealthUri(repoDir, composeFile, projectName, healthUri, apiService);
        waitForApiReady(effectiveHealthUri, readinessTimeout, pollInterval, projectName);
        return effectiveHealthUri;
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
                log.info("[{}] Health check failed: {}", runTag, ex.toString());
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

    private boolean tryRepairDockerfiles(Path repoDir, Path composeFile, String runTag, RuntimeException buildFailure) {
        String base = normalizeBaseUrl(repairBaseUrl);
        if (base.isBlank()) {
            log.warn("[{}] Repair base URL is empty; skipping repair", runTag);
            return false;
        }

        String url = base + "/repair-dockerfiles";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("repo_path", repoDir.toString());
        payload.put("compose_path", composeFile.toString());
        payload.put("error_log", truncate(buildFailure == null ? null : buildFailure.getMessage(), 20_000));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), Map.class);
            Map<String, Object> body = resp.getBody() == null ? new LinkedHashMap<>() : bodyToStringKeyMap(resp.getBody());
            boolean patched = Boolean.TRUE.equals(body.get("patched"));
            if (patched) {
                log.info("[{}] Repair service patched Dockerfiles: {}", runTag, body.get("patched_files"));
            } else {
                log.info("[{}] Repair service returned no changes", runTag);
            }
            return patched;
        } catch (Exception ex) {
            log.warn("[{}] Repair service call failed: {}", runTag, ex.getMessage());
            return false;
        }
    }

    private URI resolveEffectiveHealthUri(
            Path repoDir,
            Path composeFile,
            String projectName,
            URI requestedHealthUri,
            String apiService
    ) {
        try {
            PortBinding binding = findApiPortBindingFromCompose(composeFile, apiService);
            if (binding == null) {
                return requestedHealthUri;
            }

            Integer hostPort = resolvePublishedHostPort(repoDir, composeFile, projectName, binding.serviceName(), binding.containerPort());
            if (hostPort == null) {
                return requestedHealthUri;
            }

            URI resolved = replacePort(requestedHealthUri, hostPort);
            if (!Objects.equals(requestedHealthUri, resolved)) {
                log.info("[{}] Resolved published API port: {} -> {}", projectName, requestedHealthUri, resolved);
            }
            return resolved;
        } catch (Exception ex) {
            log.warn("[{}] Failed to resolve published port; using requested health URI. Reason: {}", projectName, ex.getMessage());
            return requestedHealthUri;
        }
    }

    private record PortBinding(String serviceName, int containerPort) {}

    private PortBinding findApiPortBindingFromCompose(Path composeFile, String apiService) {
        if (composeFile == null || !Files.exists(composeFile)) {
            return null;
        }

        Map<String, Object> root;
        try {
            String text = Files.readString(composeFile);
            Object loaded = new Yaml().load(text);
            if (!(loaded instanceof Map<?, ?> m)) {
                return null;
            }
            root = bodyToStringKeyMap(m);
        } catch (Exception ex) {
            return null;
        }

        Object servicesObj = root.get("services");
        if (!(servicesObj instanceof Map<?, ?> servicesRaw)) {
            return null;
        }
        Map<String, Object> services = bodyToStringKeyMap(servicesRaw);

        if (apiService != null && !apiService.isBlank()) {
            PortBinding binding = extractPortBinding(apiService, services.get(apiService));
            if (binding != null) {
                return binding;
            }
        }

        // Fallback: first service that exposes a port
        for (Map.Entry<String, Object> e : services.entrySet()) {
            PortBinding binding = extractPortBinding(e.getKey(), e.getValue());
            if (binding != null) {
                return binding;
            }
        }
        return null;
    }

    private PortBinding extractPortBinding(String serviceName, Object svcObj) {
        if (!(svcObj instanceof Map<?, ?> svcRaw)) {
            return null;
        }
        Map<String, Object> svc = bodyToStringKeyMap(svcRaw);
        Object portsObj = svc.get("ports");
        if (!(portsObj instanceof List<?> ports) || ports.isEmpty()) {
            return null;
        }

        Integer containerPort = parseContainerPortSpec(ports.get(0));
        if (containerPort == null || containerPort < 1 || containerPort > 65535) {
            return null;
        }
        return new PortBinding(serviceName, containerPort);
    }

    private Integer parseContainerPortSpec(Object portSpec) {
        if (portSpec instanceof Number n) {
            return n.intValue();
        }
        if (!(portSpec instanceof String s)) {
            return null;
        }

        String raw = s.trim();
        if (raw.isBlank()) {
            return null;
        }

        // Drop protocol suffix, e.g. "8082:8082/tcp"
        int slash = raw.indexOf('/');
        if (slash > 0) {
            raw = raw.substring(0, slash);
        }

        // Could be "8082", "0:8082", "127.0.0.1:0:8082"
        String[] parts = raw.split(":");
        String last = parts.length == 0 ? raw : parts[parts.length - 1];
        try {
            return Integer.parseInt(last.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer resolvePublishedHostPort(
            Path repoDir,
            Path composeFile,
            String projectName,
            String apiService,
            int containerPort
    ) {
        String out = runCommand(
                List.of(
                        "docker", "compose",
                        "-p", projectName,
                        "-f", composeFile.toString(),
                        "port", apiService,
                        String.valueOf(containerPort)
                ),
                repoDir,
                Duration.ofSeconds(30),
                projectName,
                "compose-port"
        );

        // Expected: "0.0.0.0:32768" (may include brackets for IPv6)
        Matcher m = Pattern.compile(":(\\d{2,5})").matcher(out);
        Integer port = null;
        while (m.find()) {
            port = Integer.parseInt(m.group(1));
        }
        return port;
    }

    private URI replacePort(URI uri, int port) {
        try {
            return new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    port,
                    uri.getRawPath(),
                    uri.getRawQuery(),
                    uri.getRawFragment()
            );
        } catch (Exception ex) {
            return URI.create(uri.getScheme() + "://" + uri.getHost() + ":" + port + uri.getRawPath());
        }
    }

    private String normalizeBaseUrl(String url) {
        String base = url == null ? "" : url.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen);
    }

    private Map<String, Object> bodyToStringKeyMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            Object k = e.getKey();
            if (k == null) {
                continue;
            }
            out.put(String.valueOf(k), e.getValue());
        }
        return out;
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