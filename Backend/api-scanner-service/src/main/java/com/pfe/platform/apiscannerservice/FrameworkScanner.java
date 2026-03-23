package com.pfe.platform.apiscannerservice;

import java.nio.file.Path;

/**
 * Minimal test-facing interface.
 */
public interface FrameworkScanner {
    boolean supports(ProjectMetadata metadata);

    ApiContract scan(Path projectPath);
}

