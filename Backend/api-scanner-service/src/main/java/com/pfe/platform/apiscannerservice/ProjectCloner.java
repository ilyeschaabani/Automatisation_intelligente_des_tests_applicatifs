package com.pfe.platform.apiscannerservice;

import java.nio.file.Path;

/**
 * Test-facing contract used by {@code ScannerEngineCloneAuthHandlingTests}.
 *
 * This is intentionally small: production code lives in other packages.
 */
public abstract class ProjectCloner {
    public abstract Path cloneToTemp(String repoUrl, String gitToken, String gitUsername, String gitPassword);

    public void deleteQuietly(Path path) {
        // no-op for tests by default
    }
}

