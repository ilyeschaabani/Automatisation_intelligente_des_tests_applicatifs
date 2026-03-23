package com.pfe.platform.apiscannerservice.aicode;

import com.pfe.platform.apiscannerservice.aicode.config.AiCodeAnalyzerProperties;
import com.pfe.platform.apiscannerservice.aicode.service.GitService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GitServiceDeleteTreeTests {

    @Test
    void deleteRepoQuietlyDeletesGitFirst() throws Exception {
        AiCodeAnalyzerProperties props = new AiCodeAnalyzerProperties();
        props.setGitTempRoot(System.getProperty("java.io.tmpdir"));
        GitService svc = new GitService(props);

        Path repo = Files.createTempDirectory("aicode-repo-");
        Files.createDirectories(repo.resolve(".git").resolve("objects").resolve("pack"));
        Files.writeString(repo.resolve(".git").resolve("objects").resolve("pack").resolve("p.idx"), "x");
        Files.writeString(repo.resolve("file.txt"), "y");

        svc.deleteRepoQuietly(repo);
        assertFalse(Files.exists(repo), "repo directory should be deleted");
    }
}

