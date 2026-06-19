package com.pfe.platform.msexecution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.platform.msexecution.entity.SecurityScan;
import com.pfe.platform.msexecution.entity.SecurityVulnerability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class SemgrepScanService {

    private static final Logger log = LoggerFactory.getLogger(SemgrepScanService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, SecurityVulnerability.Severity> SEVERITY_MAP = Map.of(
            "ERROR", SecurityVulnerability.Severity.HIGH,
            "WARNING", SecurityVulnerability.Severity.MEDIUM,
            "INFO", SecurityVulnerability.Severity.LOW
    );

    private static final Map<String, String> CWE_TO_OWASP = Map.ofEntries(
            Map.entry("CWE-89", "A03"),  Map.entry("CWE-564", "A03"),
            Map.entry("CWE-79", "A03"),  Map.entry("CWE-78", "A03"),
            Map.entry("CWE-77", "A03"),  Map.entry("CWE-90", "A03"),
            Map.entry("CWE-611", "A05"), Map.entry("CWE-918", "A10"),
            Map.entry("CWE-502", "A08"), Map.entry("CWE-327", "A02"),
            Map.entry("CWE-330", "A02"), Map.entry("CWE-798", "A07"),
            Map.entry("CWE-200", "A01"), Map.entry("CWE-22", "A01"),
            Map.entry("CWE-434", "A04"), Map.entry("CWE-352", "A05"),
            Map.entry("CWE-287", "A07"), Map.entry("CWE-306", "A07"),
            Map.entry("CWE-862", "A01"), Map.entry("CWE-863", "A01")
    );

    public List<SecurityVulnerability> scan(Path repoDir) throws Exception {
        Path outputFile = Files.createTempFile("semgrep-", ".json");
        try {
            List<String> cmd = List.of(
                    "semgrep", "scan",
                    "--config", "auto",
                    "--json",
                    "--output", outputFile.toString(),
                    "--timeout", "120",
                    "--max-target-bytes", "1000000",
                    repoDir.toString()
            );

            log.info("[SAST] Running Semgrep on {}", repoDir);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("PYTHONUTF8", "1");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder processOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    processOutput.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Semgrep scan timed out after 10 minutes");
            }

            log.info("[SAST] Semgrep exited with code {}", process.exitValue());

            if (!Files.exists(outputFile) || Files.size(outputFile) == 0) {
                log.warn("[SAST] No output file produced. Process output:\n{}", processOutput);
                return List.of();
            }

            return parseResults(outputFile, repoDir);
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    private List<SecurityVulnerability> parseResults(Path jsonFile, Path repoDir) throws Exception {
        JsonNode root = objectMapper.readTree(jsonFile.toFile());
        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            log.warn("[SAST] No 'results' array in Semgrep output");
            return List.of();
        }

        List<SecurityVulnerability> vulns = new ArrayList<>();
        for (JsonNode finding : results) {
            SecurityVulnerability v = new SecurityVulnerability();

            String checkId = finding.path("check_id").asText("");
            v.setTitle(buildTitle(checkId));
            v.setSource("Semgrep");
            v.setVulnType(SecurityVulnerability.VulnType.SAST);
            v.setStatus(SecurityVulnerability.VulnStatus.OPEN);

            String severity = finding.path("extra").path("severity").asText("INFO");
            v.setSeverity(SEVERITY_MAP.getOrDefault(severity, SecurityVulnerability.Severity.INFO));

            String filePath = finding.path("path").asText("");
            if (filePath.startsWith(repoDir.toString())) {
                filePath = repoDir.relativize(Path.of(filePath)).toString();
            }
            v.setFile(filePath.replace('\\', '/'));
            v.setLine(finding.path("start").path("line").asInt(0));

            JsonNode extra = finding.path("extra");
            v.setSnippet(extra.path("lines").asText("").trim());
            v.setDescription(extra.path("message").asText(""));

            String metaCwe = extractCwe(extra.path("metadata"));
            v.setCweId(metaCwe);
            v.setOwaspCategory(CWE_TO_OWASP.getOrDefault(metaCwe, "A00"));

            v.setRecommendation(extra.path("fix").asText(
                    extra.path("metadata").path("fix").asText("")));

            String references = extractReferences(extra.path("metadata"));
            v.setRisk(buildRiskDescription(severity, checkId, references));

            vulns.add(v);
        }

        log.info("[SAST] Parsed {} findings from Semgrep", vulns.size());
        return vulns;
    }

    private String buildTitle(String checkId) {
        String[] parts = checkId.split("\\.");
        String last = parts[parts.length - 1];
        return last.replace('-', ' ').replace('_', ' ');
    }

    private String extractCwe(JsonNode metadata) {
        JsonNode cweNode = metadata.path("cwe");
        if (cweNode.isArray() && cweNode.size() > 0) {
            String raw = cweNode.get(0).asText("");
            if (raw.contains(":")) return raw.split(":")[0].trim();
            return raw;
        }
        if (cweNode.isTextual()) {
            String raw = cweNode.asText("");
            if (raw.contains(":")) return raw.split(":")[0].trim();
            return raw;
        }
        return "";
    }

    private String extractReferences(JsonNode metadata) {
        JsonNode refs = metadata.path("references");
        if (!refs.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode ref : refs) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(ref.asText(""));
        }
        return sb.toString();
    }

    private String buildRiskDescription(String severity, String checkId, String references) {
        StringBuilder sb = new StringBuilder();
        sb.append("Severity: ").append(severity);
        sb.append("\nRule: ").append(checkId);
        if (!references.isEmpty()) {
            sb.append("\nReferences:\n").append(references);
        }
        return sb.toString();
    }

    public int countJavaFiles(Path repoDir) {
        try (Stream<Path> files = Files.walk(repoDir)) {
            return (int) files.filter(p -> p.toString().endsWith(".java")).count();
        } catch (Exception e) {
            return 0;
        }
    }

    public int countLinesOfCode(Path repoDir) {
        try (Stream<Path> files = Files.walk(repoDir)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .mapToInt(p -> {
                        try { return Files.readAllLines(p).size(); }
                        catch (Exception e) { return 0; }
                    }).sum();
        } catch (Exception e) {
            return 0;
        }
    }
}
