package com.pfe.platform.apiscannerservice.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.platform.apiscannerservice.Model.ProjectDetectionResult;
import com.pfe.platform.apiscannerservice.Model.ProjectMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Ollama-backed AI detector.
 *
 * Calls local Ollama HTTP API (default http://localhost:11434) with a small evidence bundle
 * (file tree + key manifests truncated) and expects STRICT JSON output.
 */
@Component
@ConditionalOnProperty(name = "scanner.ai.enabled", havingValue = "true")
public class OllamaAiFrameworkDetector implements AiFrameworkDetector {

    private static final Logger log = LoggerFactory.getLogger(OllamaAiFrameworkDetector.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final URI baseUrl;
    private final String model;
    private final Duration timeout;

    public OllamaAiFrameworkDetector(
            ObjectMapper objectMapper,
            @Value("${scanner.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${scanner.ai.ollama.model:qwen2.5-coder:7b}") String model,
            @Value("${scanner.ai.ollama.timeout:30s}") Duration timeout
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = URI.create(baseUrl);
        this.model = model;
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    @Override
    public ProjectDetectionResult detectWithAi(Path repoRoot) {
        if (repoRoot == null || !Files.exists(repoRoot) || !Files.isDirectory(repoRoot)) {
            return null;
        }

        String prompt = buildPrompt(repoRoot);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", model);
        req.put("prompt", prompt);
        req.put("stream", false);
        req.put("format", "json");

        String body;
        try {
            body = objectMapper.writeValueAsString(req);
        } catch (IOException e) {
            log.warn("ai.ollama.serialize.failed", e);
            return null;
        }

        URI uri = baseUrl.resolve("/api/generate");

        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        String raw;
        try {
            HttpResponse<String> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("ai.ollama.http.failed status={} body={}", resp.statusCode(), trim(resp.body(), 500));
                return null;
            }
            raw = resp.body();
        } catch (Exception e) {
            log.warn("ai.ollama.http.exception", e);
            return null;
        }

        // Ollama returns JSON with a "response" field containing model output.
        Map<String, Object> envelope;
        try {
            envelope = objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("ai.ollama.parse.envelope.failed raw={}", trim(raw, 500));
            return null;
        }

        Object responseObj = envelope.get("response");
        if (!(responseObj instanceof String responseText) || responseText.isBlank()) {
            log.warn("ai.ollama.empty.response raw={}", trim(raw, 500));
            return null;
        }

        Map<String, Object> aiJson;
        try {
            aiJson = objectMapper.readValue(responseText, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("ai.ollama.parse.responseJson.failed response={}", trim(responseText, 500));
            return null;
        }

        return toDetectionResult(repoRoot, aiJson);
    }

    private ProjectDetectionResult toDetectionResult(Path repoRoot, Map<String, Object> aiJson) {
        String frameworkStr = asString(aiJson.get("framework"));
        String projectRootStr = asString(aiJson.get("projectRoot"));
        String confidenceStr = asString(aiJson.get("confidence"));

        ProjectMetadata.Framework fw = parseFramework(frameworkStr);
        if (fw == ProjectMetadata.Framework.UNKNOWN) {
            return null;
        }

        Path projectRoot = resolveProjectRoot(repoRoot, projectRootStr);
        if (projectRoot == null) {
            return null;
        }

        ProjectMetadata md = new ProjectMetadata();
        md.setFramework(fw);
        md.getHints().put("detection", "ai");
        md.getHints().put("ai.model", model);
        md.getHints().put("ai.confidence", confidenceStr != null ? confidenceStr : "");
        md.getHints().put("projectRoot", projectRoot.toString());

        // optional evidence array
        Object ev = aiJson.get("evidence");
        if (ev instanceof List<?> list) {
            String joined = list.stream().limit(8).map(Object::toString).collect(Collectors.joining(" | "));
            if (!joined.isBlank()) md.getHints().put("ai.evidence", joined);
        }

        return new ProjectDetectionResult(projectRoot, md);
    }

    private Path resolveProjectRoot(Path repoRoot, String projectRootStr) {
        if (projectRootStr == null || projectRootStr.isBlank() || "/".equals(projectRootStr.trim())) {
            return repoRoot;
        }

        String p = projectRootStr.replace("\\", "/").trim();
        while (p.startsWith("./")) p = p.substring(2);
        while (p.startsWith("/")) p = p.substring(1);

        Path candidate = repoRoot.resolve(p).normalize();
        if (!candidate.startsWith(repoRoot.normalize())) {
            log.warn("ai.projectRoot.invalidTraversal projectRoot={}", projectRootStr);
            return null;
        }
        if (!Files.exists(candidate) || !Files.isDirectory(candidate)) {
            log.warn("ai.projectRoot.notFound candidate={}", candidate);
            return null;
        }
        return candidate;
    }

    private ProjectMetadata.Framework parseFramework(String s) {
        if (s == null) return ProjectMetadata.Framework.UNKNOWN;
        String normalized = s.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (ProjectMetadata.Framework f : ProjectMetadata.Framework.values()) {
            if (f.name().equals(normalized)) return f;
        }
        return ProjectMetadata.Framework.UNKNOWN;
    }

    private String buildPrompt(Path repoRoot) {
        // Evidence is intentionally limited (no full source code by default).
        String tree = buildFileTree(repoRoot);
        Map<String, String> manifests = readKeyManifests(repoRoot);

        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert backend framework detector.\n");
        sb.append("Given a repository file tree and key config files, identify the backend framework and the best project root folder (within the repo).\n");
        sb.append("Return STRICT JSON only with keys: framework, projectRoot, confidence, evidence.\n");
        sb.append("\n");
        sb.append("Allowed frameworks:\n");
        sb.append(String.join(", ", Arrays.stream(ProjectMetadata.Framework.values()).map(Enum::name).toList()));
        sb.append("\n\n");
        sb.append("Rules:\n");
        sb.append("- framework must be one of the allowed list.\n");
        sb.append("- projectRoot must be a relative path within repo (like Backend/api or .).\n");
        sb.append("- confidence should be one of: HIGH, MEDIUM, LOW.\n");
        sb.append("- evidence must be a short list of strings explaining why.\n");
        sb.append("\n--- FILE TREE (depth-limited) ---\n");
        sb.append(tree).append("\n");
        sb.append("\n--- KEY FILES (truncated) ---\n");
        for (Map.Entry<String, String> e : manifests.entrySet()) {
            sb.append("### ").append(e.getKey()).append("\n");
            sb.append(e.getValue()).append("\n\n");
        }

        return sb.toString();
    }

    private String buildFileTree(Path repoRoot) {
        final int maxDepth = 6;
        final int maxEntries = 1500;

        List<String> entries = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(repoRoot, maxDepth)) {
            paths.forEach(p -> {
                if (entries.size() >= maxEntries) return;
                if (p.equals(repoRoot)) return;
                Path rel = repoRoot.relativize(p);
                // Avoid leaking huge node_modules/target
                String relStr = rel.toString().replace('\\', '/');
                if (relStr.startsWith(".git/") || relStr.startsWith("node_modules/") || relStr.startsWith("target/") || relStr.startsWith("build/")) {
                    return;
                }
                entries.add(relStr + (Files.isDirectory(p) ? "/" : ""));
            });
        } catch (IOException ignored) {
        }
        Collections.sort(entries);
        return String.join("\n", entries);
    }

    private Map<String, String> readKeyManifests(Path repoRoot) {
        // Try common locations (repo root + first-level + backend-like folders)
        List<Path> searchRoots = new ArrayList<>();
        searchRoots.add(repoRoot);
        try (Stream<Path> kids = Files.list(repoRoot)) {
            kids.filter(Files::isDirectory).limit(30).forEach(searchRoots::add);
        } catch (IOException ignored) {
        }

        Map<String, String> out = new LinkedHashMap<>();
        for (Path r : searchRoots) {
            addIfExists(out, repoRoot, r.resolve("pom.xml"));
            addIfExists(out, repoRoot, r.resolve("build.gradle"));
            addIfExists(out, repoRoot, r.resolve("build.gradle.kts"));
            addIfExists(out, repoRoot, r.resolve("package.json"));
            addIfExists(out, repoRoot, r.resolve("pyproject.toml"));
            addIfExists(out, repoRoot, r.resolve("requirements.txt"));
            addIfExists(out, repoRoot, r.resolve("composer.json"));
            addIfExists(out, repoRoot, r.resolve("Gemfile"));
            addIfExists(out, repoRoot, r.resolve("go.mod"));
            addIfExists(out, repoRoot, r.resolve("README.md"));
            addIfExists(out, repoRoot, r.resolve("Dockerfile"));
        }

        // Keep small.
        return out.entrySet().stream().limit(12)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private void addIfExists(Map<String, String> out, Path repoRoot, Path file) {
        if (!Files.exists(file) || Files.isDirectory(file)) return;
        String rel = repoRoot.relativize(file).toString().replace('\\', '/');
        out.put(rel, trim(readSmall(file), 5000));
    }

    private String readSmall(Path file) {
        try {
            if (Files.size(file) > 2_000_000) return "";
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private String asString(Object o) {
        if (o == null) return null;
        String s = o.toString();
        return s.isBlank() ? null : s;
    }

    private String trim(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }
}

