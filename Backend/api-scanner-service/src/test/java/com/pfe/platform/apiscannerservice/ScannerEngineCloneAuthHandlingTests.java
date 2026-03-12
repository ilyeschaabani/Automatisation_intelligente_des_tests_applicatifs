package com.pfe.platform.apiscannerservice;

import com.pfe.platform.apiscannerservice.Model.ApiContract;
import com.pfe.platform.apiscannerservice.Model.ProjectDetectionResult;
import com.pfe.platform.apiscannerservice.Model.ProjectMetadata;
import com.pfe.platform.apiscannerservice.Scanner.FrameworkScanner;
import com.pfe.platform.apiscannerservice.Service.*;
import com.pfe.platform.apiscannerservice.Util.ProjectCloner;
import org.eclipse.jgit.errors.TransportException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ScannerEngineCloneAuthHandlingTests {

    @Test
    void returnsFriendlyContractWhenCloneRequiresAuth() {
        ProjectCloner cloner = new ProjectCloner() {
            @Override
            public Path cloneToTemp(String repoUrl, String gitToken, String gitUsername, String gitPassword) {
                TransportException te = new TransportException("Authentication is required but no CredentialsProvider has been registered");
                throw new IllegalStateException("Failed to clone repository: " + repoUrl, te);
            }
        };

        ProjectDetector detector = new ProjectDetector() {
            @Override
            public ProjectDetectionResult detectProject(Path repoRoot) {
                return new ProjectDetectionResult(repoRoot,
                        ProjectMetadata.builder().framework(ProjectMetadata.Framework.UNKNOWN).build());
            }
        };

        GitAuthProperties props = new GitAuthProperties();
        List<FrameworkScanner> scanners = List.of();

        ScannerEngine engine = new ScannerEngine(cloner, detector, scanners, Optional.empty(), props);

        ApiContract res = engine.scan(null, "https://github.com/example/private-repo");
        assertNotNull(res);
        assertEquals("https://github.com/example/private-repo", res.getSource());
        assertNotNull(res.getIssues());
        assertFalse(res.getIssues().isEmpty());
        assertTrue(res.getIssues().get(0).toLowerCase().contains("authentication"));
    }
}
