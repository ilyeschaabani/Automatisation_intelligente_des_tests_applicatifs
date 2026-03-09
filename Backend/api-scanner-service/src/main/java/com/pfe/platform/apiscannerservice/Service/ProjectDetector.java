package com.pfe.platform.apiscannerservice.Service;

import com.pfe.platform.apiscannerservice.Model.ProjectDetectionResult;
import com.pfe.platform.apiscannerservice.Model.ProjectMetadata;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

@Component
public class ProjectDetector {

    public ProjectMetadata detect(Path projectRoot) {
        return detectProject(projectRoot).getMetadata();
    }

    public ProjectDetectionResult detectProject(Path repoRoot) {
        List<Path> candidates = findCandidateRoots(repoRoot);
        if (candidates.isEmpty()) {
            ProjectMetadata md = new ProjectMetadata();
            md.setFramework(ProjectMetadata.Framework.UNKNOWN);
            md.getHints().put("reason", "No build markers found (pom.xml, build.gradle, package.json, *.csproj, *.sln)");
            return new ProjectDetectionResult(repoRoot, md);
        }

        ProjectDetectionResult best = null;
        int bestScore = Integer.MIN_VALUE;

        for (Path candidate : candidates) {
            ProjectMetadata md = detectSingleProject(candidate);
            int score = score(candidate, repoRoot, md);
            if (score > bestScore) {
                bestScore = score;
                best = new ProjectDetectionResult(candidate, md);
            }
        }

        if (best == null) {
            ProjectMetadata md = new ProjectMetadata();
            md.setFramework(ProjectMetadata.Framework.UNKNOWN);
            return new ProjectDetectionResult(repoRoot, md);
        }

        // Expose where detection landed; helps debug monorepos.
        best.getMetadata().getHints().put("projectRoot", best.getProjectRoot().toString());
        return best;
    }

    private List<Path> findCandidateRoots(Path repoRoot) {
        // Keep this bounded to avoid walking massive repos forever.
        final int maxCandidates = 200;
        final int maxDepth = 8;

        if (repoRoot == null || !Files.exists(repoRoot) || !Files.isDirectory(repoRoot)) return List.of();

        List<Path> roots = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(repoRoot, maxDepth)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> !isIgnoredPath(p))
                    .forEach(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        boolean marker = name.equals("pom.xml")
                                || name.equals("build.gradle")
                                || name.equals("build.gradle.kts")
                                || name.equals("package.json")
                                || name.equals("pyproject.toml")
                                || name.equals("requirements.txt")
                                || name.equals("composer.json")
                                || name.equals("gemfile")
                                || name.equals("go.mod")
                                || name.endsWith(".csproj")
                                || name.endsWith(".sln");
                        if (!marker) return;

                        Path candidateRoot = p.getParent();
                        if (candidateRoot == null) return;

                        // de-dupe
                        if (roots.stream().noneMatch(r -> r.equals(candidateRoot))) {
                            roots.add(candidateRoot);
                        }
                    });
        } catch (IOException ignored) {
        }

        if (roots.size() > maxCandidates) {
            return roots.subList(0, maxCandidates);
        }
        return roots;
    }

    private int score(Path candidate, Path repoRoot, ProjectMetadata md) {
        int s = 0;

        // Prefer known frameworks.
        s += switch (md.getFramework()) {
            case SPRING_BOOT -> 1000;
            case QUARKUS -> 980;
            case MICRONAUT -> 970;

            case NESTJS -> 900;
            case EXPRESS -> 800;
            case FASTIFY -> 780;
            case KOA -> 760;

            case DOTNET -> 700;

            case FASTAPI -> 680;
            case DJANGO -> 660;
            case FLASK -> 640;

            case LARAVEL -> 620;
            case SYMFONY -> 610;

            case RAILS -> 600;

            case GIN -> 590;

            case UNKNOWN -> 0;
        };

        // Prefer shallower directory.
        try {
            s -= repoRoot.toAbsolutePath().relativize(candidate.toAbsolutePath()).getNameCount() * 5;
        } catch (Exception ignored) {
        }

        // Tiny heuristic for monorepos.
        String p = candidate.toString().toLowerCase(Locale.ROOT);
        if (p.contains("backend")) s += 20;
        if (p.contains("server")) s += 10;
        if (p.contains("api")) s += 5;

        return s;
    }

    private ProjectMetadata detectSingleProject(Path projectRoot) {
        ProjectMetadata md = new ProjectMetadata();

        // --- Java (Spring/Quarkus/Micronaut) ---
        boolean hasPom = Files.exists(projectRoot.resolve("pom.xml"));
        boolean hasGradle = Files.exists(projectRoot.resolve("build.gradle")) || Files.exists(projectRoot.resolve("build.gradle.kts"));

        if (hasPom || hasGradle) {
            md.setLanguage("java");

            // Spring (prefer source markers, but fall back to build markers)
            boolean hasSpringSourceMarkers = containsSpringMarkers(projectRoot);
            boolean hasSpringBuildMarkers = fileContainsIgnoreCase(projectRoot.resolve("pom.xml"), "spring-boot")
                    || fileContainsIgnoreCase(projectRoot.resolve("pom.xml"), "org.springframework.boot")
                    || fileContainsIgnoreCase(projectRoot.resolve("pom.xml"), "spring-boot-starter")
                    || fileContainsIgnoreCase(projectRoot.resolve("build.gradle"), "org.springframework.boot")
                    || fileContainsIgnoreCase(projectRoot.resolve("build.gradle.kts"), "org.springframework.boot")
                    || fileContainsIgnoreCase(projectRoot.resolve("build.gradle"), "spring-boot")
                    || fileContainsIgnoreCase(projectRoot.resolve("build.gradle.kts"), "spring-boot")
                    || fileContainsIgnoreCase(projectRoot.resolve("build.gradle"), "springframework.boot")
                    || fileContainsIgnoreCase(projectRoot.resolve("build.gradle.kts"), "springframework.boot");

            boolean hasSpringConfigMarkers = Files.exists(projectRoot.resolve("src").resolve("main").resolve("resources").resolve("application.properties"))
                    || Files.exists(projectRoot.resolve("src").resolve("main").resolve("resources").resolve("application.yml"))
                    || Files.exists(projectRoot.resolve("src").resolve("main").resolve("resources").resolve("application.yaml"));

            boolean hasTypicalSpringLayout = Files.isDirectory(projectRoot.resolve("src").resolve("main").resolve("java"))
                    && Files.isDirectory(projectRoot.resolve("src").resolve("main").resolve("resources"));

            if (hasSpringSourceMarkers || hasSpringBuildMarkers || (hasTypicalSpringLayout && hasSpringConfigMarkers)) {
                md.setFramework(ProjectMetadata.Framework.SPRING_BOOT);
                md.getHints().put("build", hasPom ? "maven" : "gradle");
                if (hasSpringSourceMarkers) md.getHints().put("springDetection", "source");
                else if (hasSpringBuildMarkers) md.getHints().put("springDetection", "build");
                else md.getHints().put("springDetection", "layout+config");
                return md;
            }

            // Quarkus
            if (fileContainsIgnoreCase(projectRoot.resolve("pom.xml"), "io.quarkus")
                    || fileContainsIgnoreCase(projectRoot.resolve("build.gradle"), "io.quarkus")
                    || fileContainsIgnoreCase(projectRoot.resolve("build.gradle.kts"), "io.quarkus")) {
                md.setFramework(ProjectMetadata.Framework.QUARKUS);
                md.getHints().put("build", hasPom ? "maven" : "gradle");
                return md;
            }

            // Micronaut
            if (fileContainsIgnoreCase(projectRoot.resolve("pom.xml"), "io.micronaut")
                    || fileContainsIgnoreCase(projectRoot.resolve("build.gradle"), "io.micronaut")
                    || fileContainsIgnoreCase(projectRoot.resolve("build.gradle.kts"), "io.micronaut")) {
                md.setFramework(ProjectMetadata.Framework.MICRONAUT);
                md.getHints().put("build", hasPom ? "maven" : "gradle");
                return md;
            }
        }

        // --- Node.js (Nest/Express/Fastify/Koa) ---
        Path pkgJsonPath = projectRoot.resolve("package.json");
        if (Files.exists(pkgJsonPath)) {
            String pkg = readSmallFile(pkgJsonPath);
            if (containsAny(pkg, "\"@nestjs/core\"")) {
                md.setLanguage("typescript");
                md.setFramework(ProjectMetadata.Framework.NESTJS);
                return md;
            }
            if (containsAny(pkg, "\"express\"")) {
                md.setLanguage("javascript");
                md.setFramework(ProjectMetadata.Framework.EXPRESS);
                return md;
            }
            if (containsAny(pkg, "\"fastify\"")) {
                md.setLanguage("javascript");
                md.setFramework(ProjectMetadata.Framework.FASTIFY);
                return md;
            }
            if (containsAny(pkg, "\"koa\"")) {
                md.setLanguage("javascript");
                md.setFramework(ProjectMetadata.Framework.KOA);
                return md;
            }
        }

        // --- Python (FastAPI/Django/Flask) ---
        Path pyproject = projectRoot.resolve("pyproject.toml");
        Path reqs = projectRoot.resolve("requirements.txt");

        if (Files.exists(pyproject) || Files.exists(reqs)) {
            String deps = readSmallFile(pyproject) + "\n" + readSmallFile(reqs);
            // FastAPI
            if (deps.contains("fastapi") || hasAnyFileNamed(projectRoot, "main.py", 4, "from fastapi import")) {
                md.setLanguage("python");
                md.setFramework(ProjectMetadata.Framework.FASTAPI);
                return md;
            }
            // Django
            if (deps.contains("django") || Files.exists(projectRoot.resolve("manage.py"))) {
                md.setLanguage("python");
                md.setFramework(ProjectMetadata.Framework.DJANGO);
                return md;
            }
            // Flask
            if (deps.contains("flask") || hasAnyFileNamed(projectRoot, "app.py", 4, "from flask")) {
                md.setLanguage("python");
                md.setFramework(ProjectMetadata.Framework.FLASK);
                return md;
            }
        }

        // --- PHP (Laravel/Symfony) ---
        Path composer = projectRoot.resolve("composer.json");
        if (Files.exists(composer)) {
            String composerJson = readSmallFile(composer);
            if (composerJson.contains("laravel/framework")) {
                md.setLanguage("php");
                md.setFramework(ProjectMetadata.Framework.LARAVEL);
                return md;
            }
            if (composerJson.contains("symfony/")) {
                md.setLanguage("php");
                md.setFramework(ProjectMetadata.Framework.SYMFONY);
                return md;
            }
        }

        // --- Ruby (Rails) ---
        Path gemfile = projectRoot.resolve("Gemfile");
        if (Files.exists(gemfile)) {
            String gem = readSmallFile(gemfile);
            if (gem.contains("gem \"rails\"") || gem.contains("gem 'rails'")) {
                md.setLanguage("ruby");
                md.setFramework(ProjectMetadata.Framework.RAILS);
                return md;
            }
        }

        // --- Go (Gin) ---
        Path goMod = projectRoot.resolve("go.mod");
        if (Files.exists(goMod)) {
            String go = readSmallFile(goMod);
            if (go.contains("github.com/gin-gonic/gin")) {
                md.setLanguage("go");
                md.setFramework(ProjectMetadata.Framework.GIN);
                return md;
            }
        }

        // --- .NET ---
        if (hasAnyFileWithSuffix(projectRoot, ".csproj") || hasAnyFileWithSuffix(projectRoot, ".sln")) {
            md.setLanguage("csharp");
            md.setFramework(ProjectMetadata.Framework.DOTNET);
            return md;
        }

        md.setFramework(ProjectMetadata.Framework.UNKNOWN);
        md.getHints().put("reason", "No known framework markers matched in candidate root");
        return md;
    }

    private boolean containsSpringMarkers(Path root) {
        Path src = root.resolve("src").resolve("main").resolve("java");
        if (!Files.exists(src)) return false;
        try (Stream<Path> paths = Files.walk(src)) {
            return paths.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .filter(p -> !isIgnoredPath(p))
                    .limit(4000)
                    .anyMatch(this::isSpringFile);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isSpringFile(Path javaFile) {
        String content = readSmallFile(javaFile).toLowerCase(Locale.ROOT);
        return content.contains("org.springframework.web.bind.annotation")
                || content.contains("@restcontroller")
                || content.contains("@controller")
                || content.contains("@requestmapping")
                || content.contains("@getmapping")
                || content.contains("@postmapping")
                || content.contains("@putmapping")
                || content.contains("@deletemapping")
                || content.contains("@patchmapping");
    }

    private String readSmallFile(Path path) {
        if (path == null || !Files.exists(path) || Files.isDirectory(path)) return "";
        try {
            if (Files.size(path) > 2_000_000) return "";
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private boolean fileContainsIgnoreCase(Path file, String needle) {
        if (file == null || !Files.exists(file) || Files.isDirectory(file)) return false;
        if (needle == null || needle.isBlank()) return false;
        String hay = readSmallFile(file).toLowerCase(Locale.ROOT);
        return hay.contains(needle.toLowerCase(Locale.ROOT));
    }

    private boolean fileContains(Path file, String needle) {
        if (file == null || !Files.exists(file) || Files.isDirectory(file)) return false;
        return readSmallFile(file).contains(needle);
    }

    private boolean isIgnoredPath(Path p) {
        if (p == null) return false;
        String s = p.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return s.contains("/target/")
                || s.contains("/node_modules/")
                || s.contains("/.git/")
                || s.contains("/.idea/")
                || s.contains("/.gradle/")
                || s.contains("/build/")
                || s.contains("/dist/");
    }

    private boolean hasAnyFileWithSuffix(Path root, String suffix) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .anyMatch(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(suffix));
        } catch (IOException e) {
            return false;
        }
    }

    private boolean containsAny(String haystack, String... needles) {
        if (haystack == null || haystack.isEmpty()) return false;
        for (String n : needles) {
            if (n != null && !n.isEmpty() && haystack.contains(n)) return true;
        }
        return false;
    }

    private boolean hasAnyFileNamed(Path root, String filename, int maxDepth, String mustContain) {
        try (Stream<Path> paths = Files.walk(root, maxDepth)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(filename))
                    .anyMatch(p -> readSmallFile(p).contains(mustContain));
        } catch (IOException e) {
            return false;
        }
    }
}
