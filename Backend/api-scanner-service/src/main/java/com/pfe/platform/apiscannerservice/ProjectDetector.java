package com.pfe.platform.apiscannerservice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class ProjectDetector {

    public ProjectDetectionResult detectProject(Path repoRoot) {
        if (repoRoot == null) throw new IllegalArgumentException("repoRoot must not be null");
        if (!Files.isDirectory(repoRoot)) throw new IllegalArgumentException("repoRoot must be a directory: " + repoRoot);

        List<Path> dirs = new ArrayList<>();
        try (Stream<Path> s = Files.walk(repoRoot)) {
            s.filter(Files::isDirectory).forEach(dirs::add);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to walk repo: " + repoRoot, e);
        }

        // Prefer deeper directories first (monorepo scenario).
        dirs.sort(Comparator.comparingInt((Path p) -> p.getNameCount()).reversed());

        for (Path dir : dirs) {
            ProjectMetadata md = detectInDirectory(dir);
            if (md.getFramework() != ProjectMetadata.Framework.UNKNOWN) {
                md.getHints().putIfAbsent("projectRoot", repoRoot.relativize(dir).toString());
                return new ProjectDetectionResult(dir.toAbsolutePath().normalize(), md);
            }
        }

        return new ProjectDetectionResult(repoRoot.toAbsolutePath().normalize(), ProjectMetadata.builder().framework(ProjectMetadata.Framework.UNKNOWN).build());
    }

    private ProjectMetadata detectInDirectory(Path dir) {
        // 1) Spring Boot
        Path pom = dir.resolve("pom.xml");
        if (Files.exists(pom)) {
            String txt = readQuietly(pom);
            boolean springParent = txt != null && txt.contains("spring-boot-starter-parent");
            boolean hasSpringJava = containsTextInSources(dir, "org.springframework.web.bind.annotation.RestController", ".java", ".kt");
            boolean springGeneric = containsTextInSources(dir, "org.springframework", ".java", ".kt");

            if (springParent) {
                ProjectMetadata md = ProjectMetadata.builder().framework(ProjectMetadata.Framework.SPRING_BOOT).build();
                md.getHints().put("springDetection", "build");
                return md;
            }
            if (Files.exists(dir.resolve("build.gradle")) || Files.exists(dir.resolve("build.gradle.kts"))) {
                if (springGeneric) {
                    return ProjectMetadata.builder().framework(ProjectMetadata.Framework.SPRING_BOOT).build();
                }
            }
            if (hasSpringJava) {
                return ProjectMetadata.builder().framework(ProjectMetadata.Framework.SPRING_BOOT).build();
            }

            // Quarkus
            if (txt != null && txt.contains("io.quarkus")) {
                return ProjectMetadata.builder().framework(ProjectMetadata.Framework.QUARKUS).build();
            }
        }

        // Micronaut from Gradle
        Path gradle = dir.resolve("build.gradle");
        if (Files.exists(gradle)) {
            String txt = readQuietly(gradle);
            if (txt != null && txt.contains("io.micronaut")) {
                return ProjectMetadata.builder().framework(ProjectMetadata.Framework.MICRONAUT).build();
            }
        }

        // 2) Express
        Path pkg = dir.resolve("package.json");
        if (Files.exists(pkg)) {
            String txt = readQuietly(pkg);
            if (txt != null && txt.toLowerCase().contains("\"express\"")) {
                return ProjectMetadata.builder().framework(ProjectMetadata.Framework.EXPRESS).build();
            }
        }

        // 3) Python requirements
        Path req = dir.resolve("requirements.txt");
        if (Files.exists(req)) {
            String txt = readQuietly(req);
            if (txt != null) {
                String lower = txt.toLowerCase();
                if (lower.contains("fastapi")) return ProjectMetadata.builder().framework(ProjectMetadata.Framework.FASTAPI).build();
                if (lower.contains("django")) return ProjectMetadata.builder().framework(ProjectMetadata.Framework.DJANGO).build();
                if (lower.contains("flask")) return ProjectMetadata.builder().framework(ProjectMetadata.Framework.FLASK).build();
            }
        }

        // 4) Laravel / Symfony from composer.json
        Path composer = dir.resolve("composer.json");
        if (Files.exists(composer)) {
            String txt = readQuietly(composer);
            if (txt != null) {
                String lower = txt.toLowerCase();
                if (lower.contains("laravel/framework")) return ProjectMetadata.builder().framework(ProjectMetadata.Framework.LARAVEL).build();
                if (lower.contains("symfony/framework-bundle")) return ProjectMetadata.builder().framework(ProjectMetadata.Framework.SYMFONY).build();
            }
        }

        // 5) Rails from Gemfile
        Path gemfile = dir.resolve("Gemfile");
        if (Files.exists(gemfile)) {
            String txt = readQuietly(gemfile);
            if (txt != null && txt.toLowerCase().contains("gem 'rails'")) {
                return ProjectMetadata.builder().framework(ProjectMetadata.Framework.RAILS).build();
            }
        }

        // 6) Gin from go.mod
        Path gomod = dir.resolve("go.mod");
        if (Files.exists(gomod)) {
            String txt = readQuietly(gomod);
            if (txt != null && txt.contains("github.com/gin-gonic/gin")) {
                return ProjectMetadata.builder().framework(ProjectMetadata.Framework.GIN).build();
            }
        }

        return ProjectMetadata.builder().framework(ProjectMetadata.Framework.UNKNOWN).build();
    }

    private boolean containsTextInSources(Path dir, String needle, String... exts) {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        for (String e : exts) {
                            if (name.endsWith(e)) return true;
                        }
                        return false;
                    })
                    .anyMatch(p -> {
                        String txt = readQuietly(p);
                        return txt != null && txt.contains(needle);
                    });
        } catch (IOException e) {
            return false;
        }
    }

    private String readQuietly(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
