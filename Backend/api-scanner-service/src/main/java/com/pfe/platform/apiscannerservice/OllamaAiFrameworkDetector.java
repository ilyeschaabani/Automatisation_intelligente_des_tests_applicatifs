package com.pfe.platform.apiscannerservice;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Minimal class for unit tests that validate safe path resolution and framework parsing.
 *
 * No actual HTTP calls are implemented here.
 */
public class OllamaAiFrameworkDetector {

    public OllamaAiFrameworkDetector(ObjectMapper objectMapper, String baseUrl, String model, Duration timeout) {
        // parameters kept for compatibility with older code/tests
    }

    @SuppressWarnings("unused")
    private Path resolveProjectRoot(Path repoRoot, String relativePath) {
        if (repoRoot == null || relativePath == null) return null;
        Path resolved = repoRoot.resolve(relativePath).toAbsolutePath().normalize();
        Path rr = repoRoot.toAbsolutePath().normalize();
        if (!resolved.startsWith(rr)) return null;
        if (!Files.isDirectory(resolved)) return null;
        return resolved;
    }

    @SuppressWarnings("unused")
    private ProjectMetadata.Framework parseFramework(String value) {
        if (value == null) return ProjectMetadata.Framework.UNKNOWN;
        String v = value.trim().toLowerCase();
        return switch (v) {
            case "spring_boot", "springboot", "spring-boot", "spring" -> ProjectMetadata.Framework.SPRING_BOOT;
            case "express", "expressjs", "node_express" -> ProjectMetadata.Framework.EXPRESS;
            case "fastapi" -> ProjectMetadata.Framework.FASTAPI;
            case "django" -> ProjectMetadata.Framework.DJANGO;
            case "flask" -> ProjectMetadata.Framework.FLASK;
            case "laravel" -> ProjectMetadata.Framework.LARAVEL;
            case "symfony" -> ProjectMetadata.Framework.SYMFONY;
            case "rails", "ruby_on_rails" -> ProjectMetadata.Framework.RAILS;
            case "gin" -> ProjectMetadata.Framework.GIN;
            case "quarkus" -> ProjectMetadata.Framework.QUARKUS;
            case "micronaut" -> ProjectMetadata.Framework.MICRONAUT;
            default -> ProjectMetadata.Framework.UNKNOWN;
        };
    }
}

