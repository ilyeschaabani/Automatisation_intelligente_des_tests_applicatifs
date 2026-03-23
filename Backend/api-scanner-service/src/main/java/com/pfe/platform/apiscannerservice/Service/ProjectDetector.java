package com.pfe.platform.apiscannerservice.Service;

import com.pfe.platform.apiscannerservice.Model.ProjectDetectionResult;
import com.pfe.platform.apiscannerservice.Model.ProjectMetadata;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ProjectDetector {

    public ProjectDetectionResult detectProject(Path repoRoot) {
        if (repoRoot == null) throw new IllegalArgumentException("repoRoot must not be null");
        if (!Files.isDirectory(repoRoot)) throw new IllegalArgumentException("repoRoot must be a directory: " + repoRoot);

        List<Path> candidates = new ArrayList<>();
        try {
            Files.walk(repoRoot)
                    .filter(Files::isDirectory)
                    .forEach(candidates::add);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to walk repo: " + repoRoot, e);
        }

        // Prefer deeper matches first (monorepo).
        candidates.sort(Comparator.comparingInt((Path p) -> p.getNameCount()).reversed());

        for (Path dir : candidates) {
            ProjectMetadata.Framework fw = detectFrameworkInDir(dir);
            if (fw != ProjectMetadata.Framework.UNKNOWN) {
                ProjectMetadata md = ProjectMetadata.builder().framework(fw).build();
                md.getHints().put("projectRoot", repoRoot.relativize(dir).toString());
                return new ProjectDetectionResult(dir.toAbsolutePath().normalize(), md);
            }
        }

        return new ProjectDetectionResult(repoRoot.toAbsolutePath().normalize(), ProjectMetadata.builder().framework(ProjectMetadata.Framework.UNKNOWN).build());
    }

    private ProjectMetadata.Framework detectFrameworkInDir(Path dir) {
        // Spring Boot: pom.xml/gradle + typical annotation import
        if (Files.exists(dir.resolve("pom.xml")) || Files.exists(dir.resolve("build.gradle")) || Files.exists(dir.resolve("build.gradle.kts"))) {
            if (containsTextInAnySource(dir, "org.springframework", ".java", ".kt")) {
                return ProjectMetadata.Framework.SPRING_BOOT;
            }
        }

        // Node Express
        Path pkg = dir.resolve("package.json");
        if (Files.exists(pkg)) {
            String txt = readQuietly(pkg);
            if (txt != null && txt.toLowerCase().contains("\"express\"")) {
                return ProjectMetadata.Framework.EXPRESS;
            }
        }

        // Python requirements
        Path req = dir.resolve("requirements.txt");
        if (Files.exists(req)) {
            String txt = readQuietly(req);
            if (txt != null) {
                String lower = txt.toLowerCase();
                if (lower.contains("fastapi")) return ProjectMetadata.Framework.FASTAPI;
                if (lower.contains("django")) return ProjectMetadata.Framework.DJANGO;
                if (lower.contains("flask")) return ProjectMetadata.Framework.FLASK;
            }
        }

        return ProjectMetadata.Framework.UNKNOWN;
    }

    private boolean containsTextInAnySource(Path dir, String needle, String... exts) {
        try {
            return Files.walk(dir)
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        for (String e : exts) {
                            if (n.endsWith(e)) return true;
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

