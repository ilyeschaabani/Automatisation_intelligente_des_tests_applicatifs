package com.pfe.platform.apiscannerservice.Service;

import com.pfe.platform.apiscannerservice.Model.ProjectDetectionResult;

import java.nio.file.Path;

/**
 * Optional AI-assisted framework detector used as a fallback when heuristic detection is UNKNOWN.
 *
 * Contract:
 * - Input: repo root path (cloned or local)
 * - Output: (projectRoot, metadata) guess, or null if not available.
 */
public interface AiFrameworkDetector {
    ProjectDetectionResult detectWithAi(Path repoRoot);
}

