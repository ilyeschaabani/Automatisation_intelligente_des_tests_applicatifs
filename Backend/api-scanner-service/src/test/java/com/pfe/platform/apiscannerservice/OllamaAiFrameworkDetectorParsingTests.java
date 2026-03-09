package com.pfe.platform.apiscannerservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.platform.apiscannerservice.Model.ProjectMetadata;
import com.pfe.platform.apiscannerservice.Service.OllamaAiFrameworkDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class OllamaAiFrameworkDetectorParsingTests {

    @TempDir
    Path repoRoot;

    @Test
    void rejectsPathTraversal() throws Exception {
        Files.writeString(repoRoot.resolve("pom.xml"), "<project></project>", StandardCharsets.UTF_8);

        OllamaAiFrameworkDetector detector = new OllamaAiFrameworkDetector(new ObjectMapper(), "http://localhost:11434", "test", Duration.ofSeconds(1));

        // call internal conversion via reflection-free path: detectWithAi isn't used here (needs HTTP)
        // Instead, validate behavior indirectly by creating a minimal JSON map and invoking resolve logic through JSON contract.
        // We'll just assert parsing framework works via allowed enum and that traversal is rejected at runtime by resolveProjectRoot.

        var method = OllamaAiFrameworkDetector.class.getDeclaredMethod("resolveProjectRoot", Path.class, String.class);
        method.setAccessible(true);

        Object res = method.invoke(detector, repoRoot, "../outside");
        assertNull(res);
    }

    @Test
    void resolvesRelativeProjectRoot() throws Exception {
        Path backend = repoRoot.resolve("Backend").resolve("api");
        Files.createDirectories(backend);

        OllamaAiFrameworkDetector detector = new OllamaAiFrameworkDetector(new ObjectMapper(), "http://localhost:11434", "test", Duration.ofSeconds(1));
        var method = OllamaAiFrameworkDetector.class.getDeclaredMethod("resolveProjectRoot", Path.class, String.class);
        method.setAccessible(true);

        Object res = method.invoke(detector, repoRoot, "Backend/api");
        assertNotNull(res);
        assertEquals(backend.toAbsolutePath().normalize(), ((Path) res).toAbsolutePath().normalize());

        var parse = OllamaAiFrameworkDetector.class.getDeclaredMethod("parseFramework", String.class);
        parse.setAccessible(true);
        Object fw = parse.invoke(detector, "spring_boot");
        assertEquals(ProjectMetadata.Framework.SPRING_BOOT, fw);
    }
}

