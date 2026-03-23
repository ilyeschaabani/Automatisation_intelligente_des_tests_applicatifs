package com.pfe.platform.apiscannerservice.aicode;

import com.pfe.platform.apiscannerservice.aicode.config.AiCodeAnalyzerProperties;
import com.pfe.platform.apiscannerservice.aicode.service.GitService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GitServiceTests {

    @Test
    void deleteRepoQuietlyDeletesNestedStructure() throws Exception {
        AiCodeAnalyzerProperties props = new AiCodeAnalyzerProperties();
        props.setGitTempRoot(System.getProperty("java.io.tmpdir"));
        GitService svc = new GitService(props);

        Path dir = Files.createTempDirectory("aicode-del-");
        Files.createDirectories(dir.resolve("a").resolve("b"));
        Files.writeString(dir.resolve("a").resolve("b").resolve("x.txt"), "hi");

        svc.deleteRepoQuietly(dir);
        assertFalse(Files.exists(dir));
    }
}

