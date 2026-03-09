package com.pfe.platform.apiscannerservice.Service;

import com.pfe.platform.apiscannerservice.Model.ProjectDetectionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Default implementation: disabled unless you plug a real AI provider.
 */
@Component
@ConditionalOnProperty(name = "scanner.ai.provider", havingValue = "noop")
public class NoopAiFrameworkDetector implements AiFrameworkDetector {

    private static final Logger log = LoggerFactory.getLogger(NoopAiFrameworkDetector.class);

    @Override
    public ProjectDetectionResult detectWithAi(Path repoRoot) {
        log.info("ai.detect.skipped reason=no-provider repoRoot={}", repoRoot);
        return null;
    }
}
