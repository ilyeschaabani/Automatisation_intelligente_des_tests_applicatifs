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
import java.util.stream.Stream;

@Service
public class DependencyCheckService {

    private static final Logger log = LoggerFactory.getLogger(DependencyCheckService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${security.nvd.api-key:}")
    private String nvdApiKey;

    private static final Map<String, SecurityVulnerability.Severity> CVSS_SEVERITY = Map.of(
            "CRITICAL", SecurityVulnerability.Severity.CRITICAL,
            "HIGH", SecurityVulnerability.Severity.HIGH,
            "MEDIUM", SecurityVulnerability.Severity.MEDIUM,
            "LOW", SecurityVulnerability.Severity.LOW
    );

    public List<SecurityVulnerability> scan(Path repoDir) throws Exception {
        Path pomRoot = findPomRoot(repoDir);
        log.info("[SCA] Using pom.xml root: {}", pomRoot);

        Path reportDir = pomRoot.resolve("target");
        Files.createDirectories(reportDir);

        injectPlugin(pomRoot);

        String mvnCmd = resolveMavenCommand(pomRoot);
        List<String> cmd = new ArrayList<>(List.of(
                mvnCmd,
                "dependency-check:check",
                "-DfailBuildOnCVSS=11",
                "-Dformat=JSON",
                "-DprettyPrint=true",
                "-DretireJsAnalyzerEnabled=false",
                "-DnodeAnalyzerEnabled=false",
                "-DfailOnError=false"
        ));
        if (nvdApiKey != null && !nvdApiKey.isBlank()) {
            cmd.add("-DnvdApiKey=" + nvdApiKey);
        }

        log.info("[SCA] Running OWASP Dependency-Check on {}", pomRoot);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(pomRoot.toFile());
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

        boolean finished = process.waitFor(15, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Dependency-Check timed out after 15 minutes");
        }

        log.info("[SCA] Dependency-Check exited with code {}", process.exitValue());

        Path reportFile = reportDir.resolve("dependency-check-report.json");
        if (!Files.exists(reportFile)) {
            log.warn("[SCA] No report file found. Process output:\n{}", processOutput);
            return List.of();
        }

        return parseResults(reportFile);
    }

    private Path findPomRoot(Path repoDir) throws Exception {
        Path directPom = repoDir.resolve("pom.xml");
        if (Files.exists(directPom)) return repoDir;

        try (Stream<Path> walk = Files.walk(repoDir, 3)) {
            Path found = walk
                    .filter(p -> p.getFileName().toString().equals("pom.xml"))
                    .filter(p -> !p.toString().contains(".git"))
                    .map(Path::getParent)
                    .findFirst()
                    .orElse(null);
            if (found != null) return found;
        }
        throw new RuntimeException("No pom.xml found in " + repoDir + " (searched up to 3 levels deep)");
    }

    private void injectPlugin(Path repoDir) throws Exception {
        Path pomFile = repoDir.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            throw new RuntimeException("No pom.xml found in " + repoDir);
        }

        String pomContent = Files.readString(pomFile);
        if (pomContent.contains("dependency-check-maven")) {
            log.info("[SCA] dependency-check-maven plugin already present");
            return;
        }

        String pluginBlock = """
                        <plugin>
                            <groupId>org.owasp</groupId>
                            <artifactId>dependency-check-maven</artifactId>
                            <version>9.0.9</version>
                        </plugin>
            """;

        if (pomContent.contains("<plugins>")) {
            pomContent = pomContent.replace("<plugins>", "<plugins>\n" + pluginBlock);
        } else if (pomContent.contains("<build>")) {
            pomContent = pomContent.replace("<build>",
                    "<build>\n        <plugins>\n" + pluginBlock + "        </plugins>");
        } else {
            pomContent = pomContent.replace("</project>",
                    "    <build>\n        <plugins>\n" + pluginBlock +
                            "        </plugins>\n    </build>\n</project>");
        }

        Files.writeString(pomFile, pomContent);
        log.info("[SCA] Injected dependency-check-maven plugin into pom.xml");
    }

    private String resolveMavenCommand(Path repoDir) {
        Path mvnw = repoDir.resolve("mvnw");
        Path mvnwCmd = repoDir.resolve("mvnw.cmd");
        if (Files.exists(mvnwCmd)) return mvnwCmd.toString();
        if (Files.exists(mvnw)) return mvnw.toString();
        return "mvn";
    }

    private List<SecurityVulnerability> parseResults(Path reportFile) throws Exception {
        JsonNode root = objectMapper.readTree(reportFile.toFile());
        JsonNode dependencies = root.path("dependencies");
        if (!dependencies.isArray()) return List.of();

        List<SecurityVulnerability> vulns = new ArrayList<>();

        for (JsonNode dep : dependencies) {
            JsonNode vulnerabilities = dep.path("vulnerabilities");
            if (!vulnerabilities.isArray() || vulnerabilities.isEmpty()) continue;

            String fileName = dep.path("fileName").asText("unknown");
            String filePath = dep.path("filePath").asText("");

            for (JsonNode cve : vulnerabilities) {
                SecurityVulnerability v = new SecurityVulnerability();

                String cveName = cve.path("name").asText("Unknown CVE");
                v.setTitle(cveName + " in " + fileName);
                v.setSource("OWASP Dependency-Check");
                v.setVulnType(SecurityVulnerability.VulnType.SCA);
                v.setStatus(SecurityVulnerability.VulnStatus.OPEN);

                String severity = cve.path("severity").asText("MEDIUM");
                v.setSeverity(CVSS_SEVERITY.getOrDefault(severity, SecurityVulnerability.Severity.MEDIUM));

                JsonNode cwes = cve.path("cwes");
                if (cwes.isArray() && cwes.size() > 0) {
                    v.setCweId("CWE-" + cwes.get(0).asText(""));
                }
                v.setOwaspCategory("A06");

                v.setDescription(cve.path("description").asText(""));
                v.setFile(fileName);
                v.setSnippet("Dependency: " + filePath);

                double cvssScore = cve.path("cvssv3").path("baseScore").asDouble(
                        cve.path("cvssv2").path("score").asDouble(0));
                v.setRisk("CVSS Score: " + cvssScore + " (" + severity + ")");

                JsonNode refs = cve.path("references");
                if (refs.isArray() && refs.size() > 0) {
                    StringBuilder rec = new StringBuilder("References:\n");
                    for (JsonNode ref : refs) {
                        rec.append("- ").append(ref.path("url").asText("")).append("\n");
                    }
                    v.setRecommendation(rec.toString());
                }

                vulns.add(v);
            }
        }

        log.info("[SCA] Parsed {} CVE findings from Dependency-Check", vulns.size());
        return vulns;
    }
}
