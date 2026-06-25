package com.pfe.platform.msexecution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.platform.msexecution.entity.SecurityVulnerability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

@Service
public class ZapScanService {

    private static final Logger log = LoggerFactory.getLogger(ZapScanService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${security.zap.docker-image:zaproxy/zap-stable}")
    private String zapDockerImage;

    @Value("${security.zap.timeout-minutes:15}")
    private int timeoutMinutes;

    /** Minutes max pour le spider (découverte des URLs). */
    @Value("${security.zap.spider-minutes:1}")
    private int spiderMinutes;

    /** Minutes max d'attente du passive scan (borne le temps total du scan). */
    @Value("${security.zap.scan-minutes:3}")
    private int scanMinutes;

    private static final Map<String, SecurityVulnerability.Severity> RISK_MAP = Map.of(
            "0", SecurityVulnerability.Severity.INFO,
            "1", SecurityVulnerability.Severity.LOW,
            "2", SecurityVulnerability.Severity.MEDIUM,
            "3", SecurityVulnerability.Severity.HIGH
    );

    private static final Map<Integer, String> CWE_OWASP_MAP = Map.ofEntries(
            Map.entry(79, "A03"), Map.entry(89, "A03"), Map.entry(78, "A03"),
            Map.entry(352, "A05"), Map.entry(614, "A02"), Map.entry(693, "A05"),
            Map.entry(16, "A05"), Map.entry(200, "A01"), Map.entry(525, "A04"),
            Map.entry(829, "A08"), Map.entry(345, "A08")
    );

    public List<SecurityVulnerability> scan(String targetUrl) throws Exception {
        String effectiveUrl = resolveDockerUrl(targetUrl);
        Path reportFile = Files.createTempFile("zap-report-", ".json");
        try {
            List<String> cmd = buildDockerCommand(effectiveUrl, reportFile);

            log.info("[DAST] Launching OWASP ZAP baseline scan against {} (effective: {})", targetUrl, effectiveUrl);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder processOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    processOutput.append(line).append("\n");
                    if (line.contains("FAIL") || line.contains("WARN") || line.contains("PASS")) {
                        log.info("[DAST] {}", line.trim());
                    }
                }
            }

            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("ZAP scan timed out after " + timeoutMinutes + " minutes");
            }

            int exitCode = process.exitValue();
            log.info("[DAST] ZAP exited with code {} (0=pass, 1=warn, 2=fail)", exitCode);

            if (!Files.exists(reportFile) || Files.size(reportFile) == 0) {
                log.warn("[DAST] No report file. Process output:\n{}", processOutput);
                return List.of();
            }

            // On parse en remettant l'URL d'origine (pas le hostname Docker interne).
            return parseResults(reportFile, targetUrl);
        } finally {
            Files.deleteIfExists(reportFile);
        }
    }

    /** Remplace host.docker.internal (usage interne Docker) par l'host réel pour l'affichage. */
    private String unmapDockerHost(String uri, String originalUrl) {
        if (uri == null || uri.isBlank() || !uri.contains("host.docker.internal")) return uri;
        String host = "localhost";
        try {
            String h = java.net.URI.create(originalUrl).getHost();
            if (h != null && !h.isBlank()) host = h;
        } catch (Exception ignored) {}
        return uri.replace("host.docker.internal", host);
    }

    private String resolveDockerUrl(String url) {
        if (url == null) return url;
        return url
                .replace("localhost", "host.docker.internal")
                .replace("127.0.0.1", "host.docker.internal");
    }

    private List<String> buildDockerCommand(String targetUrl, Path reportFile) {
        Path reportDir = reportFile.getParent();
        String reportName = reportFile.getFileName().toString();

        return List.of(
                "docker", "run", "--rm",
                "--add-host", "host.docker.internal:host-gateway",
                "-v", reportDir.toString() + ":/zap/wrk:rw",
                zapDockerImage,
                "zap-baseline.py",
                "-t", targetUrl,
                "-J", reportName,
                "-m", String.valueOf(spiderMinutes),  // spider borné (défaut 1 min)
                "-T", String.valueOf(scanMinutes),     // passive scan borné (défaut 3 min)
                "-I"
        );
    }

    private List<SecurityVulnerability> parseResults(Path jsonFile, String targetUrl) throws Exception {
        JsonNode root = objectMapper.readTree(jsonFile.toFile());
        List<SecurityVulnerability> vulns = new ArrayList<>();

        JsonNode sites = root.path("site");
        if (!sites.isArray()) return vulns;

        for (JsonNode site : sites) {
            JsonNode alerts = site.path("alerts");
            if (!alerts.isArray()) continue;

            for (JsonNode alert : alerts) {
                SecurityVulnerability v = new SecurityVulnerability();

                v.setTitle(alert.path("name").asText("Unknown Alert"));
                v.setSource("OWASP ZAP");
                v.setVulnType(SecurityVulnerability.VulnType.DAST);
                v.setStatus(SecurityVulnerability.VulnStatus.OPEN);

                String riskCode = alert.path("riskcode").asText("0");
                v.setSeverity(RISK_MAP.getOrDefault(riskCode, SecurityVulnerability.Severity.INFO));

                int cweId = alert.path("cweid").asInt(0);
                v.setCweId(cweId > 0 ? "CWE-" + cweId : "");
                v.setOwaspCategory(CWE_OWASP_MAP.getOrDefault(cweId, "A00"));

                v.setDescription(alert.path("desc").asText(""));
                v.setRisk(alert.path("riskdesc").asText(""));
                v.setRecommendation(alert.path("solution").asText(""));
                v.setEvidence(alert.path("evidence").asText(""));

                JsonNode instances = alert.path("instances");
                if (instances.isArray() && instances.size() > 0) {
                    JsonNode first = instances.get(0);
                    v.setEndpoint(unmapDockerHost(first.path("uri").asText(""), targetUrl));
                    v.setHttpMethod(first.path("method").asText(""));
                    v.setParameter(first.path("param").asText(""));
                    if (v.getEvidence() == null || v.getEvidence().isEmpty()) {
                        v.setEvidence(first.path("evidence").asText(""));
                    }
                }

                vulns.add(v);
            }
        }

        log.info("[DAST] Parsed {} alerts from ZAP scan of {}", vulns.size(), targetUrl);
        return vulns;
    }
}
