package com.pfe.platform.apiscannerservice.aicode;

import com.pfe.platform.apiscannerservice.aicode.config.AiCodeAnalyzerProperties;
import com.pfe.platform.apiscannerservice.aicode.service.RepositoryScannerService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryScannerServiceTests {

    @Test
    void excludesNodeModulesAndGitAndFiltersExtensions() throws Exception {
        Path root = Files.createTempDirectory("aicode-test-");
        try {
            Files.createDirectories(root.resolve("node_modules"));
            Files.writeString(root.resolve("node_modules").resolve("bad.js"), "alert(1)");

            Files.createDirectories(root.resolve(".git"));
            Files.writeString(root.resolve(".git").resolve("config"), "secret");

            Files.writeString(root.resolve("good.java"), "class A {}");
            Files.writeString(root.resolve("notes.txt"), "ignore");

            AiCodeAnalyzerProperties props = new AiCodeAnalyzerProperties();
            props.setMaxFiles(100);
            RepositoryScannerService svc = new RepositoryScannerService(props);

            var ingested = svc.ingest(root);
            assertEquals(1, ingested.size());
            assertEquals(Path.of("good.java"), ingested.get(0).path());
        } finally {
            // best-effort cleanup
            Files.walk(root)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
        }
    }
}

