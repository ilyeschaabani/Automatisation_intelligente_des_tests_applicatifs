package com.pfe.platform.ms_gestion.service;

import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Clones a Git repo, finds a target class, and extracts a compact skeleton
 * (package + fields + method signatures only — no method bodies).
 * Stays well within the 4GB VRAM budget of deepseek-coder:6.7b.
 */
@Service
public class SkeletonExtractorService {

    private static final Logger log = LoggerFactory.getLogger(SkeletonExtractorService.class);

    @Value("${github.token:}")
    private String githubToken;

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern CLASS_PATTERN =
            Pattern.compile("(?m)^\\s*(public\\s+)?(abstract\\s+)?(class|interface|enum)\\s+(\\w+)[^{]*\\{");
    private static final Pattern FIELD_PATTERN =
            Pattern.compile("(?m)^\\s*(private|protected|public)\\s+[\\w<>\\[\\],. ]+\\s+(\\w+)\\s*;");
    private static final Pattern METHOD_PATTERN =
            Pattern.compile("(?m)^\\s*(public|protected)\\s+[\\w<>\\[\\],. ]+\\s+\\w+\\s*\\([^)]*\\)\\s*(throws[^{]+)?\\{");

    /**
     * @param gitRepoUrl  the repository URL to clone
     * @param branch      branch name (e.g. "main")
     * @param modulePath  optional sub-module path inside the repo (e.g. "user-service")
     * @param className   simple class name to find (e.g. "UserService")
     * @return compact skeleton string, or null if class not found
     */
    public String extractSkeleton(String gitRepoUrl, String branch, String modulePath, String className) {
        if (gitRepoUrl == null || gitRepoUrl.isBlank() || className == null || className.isBlank()) {
            return null;
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("skeleton-");
            log.info("Cloning {} @ {} for skeleton extraction", gitRepoUrl, branch);

            String effectiveUrl = (githubToken != null && !githubToken.isBlank() && gitRepoUrl.startsWith("https://"))
                    ? gitRepoUrl.replace("https://", "https://oauth2:" + githubToken + "@")
                    : gitRepoUrl;
            Git.cloneRepository()
                    .setURI(effectiveUrl)
                    .setDirectory(tempDir.toFile())
                    .setBranch(branch != null ? branch : "main")
                    .setDepth(1)
                    .call()
                    .close();

            Path searchRoot = tempDir;
            if (modulePath != null && !modulePath.isBlank()) {
                searchRoot = tempDir.resolve(modulePath.trim());
            }
            Path mainJava = searchRoot.resolve("src/main/java");
            if (!Files.isDirectory(mainJava)) {
                mainJava = searchRoot;
            }

            Path classFile = findClassFile(mainJava, className);
            if (classFile == null) {
                log.warn("Class {} not found under {}", className, mainJava);
                return null;
            }

            String source = Files.readString(classFile, StandardCharsets.UTF_8);
            return buildSkeleton(source, className);

        } catch (Exception e) {
            log.error("Skeleton extraction failed for class {} in {}: {}", className, gitRepoUrl, e.getMessage());
            return null;
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private Path findClassFile(Path root, String className) throws IOException {
        if (!Files.isDirectory(root)) return null;
        String fileName = className + ".java";
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName() != null && p.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElse(null);
        }
    }

    private String buildSkeleton(String source, String className) {
        List<String> lines = new ArrayList<>();

        // Package declaration
        Matcher pkgMatcher = PACKAGE_PATTERN.matcher(source);
        if (pkgMatcher.find()) {
            lines.add("package " + pkgMatcher.group(1) + ";");
            lines.add("");
        }

        // Class declaration line
        Matcher classMatcher = CLASS_PATTERN.matcher(source);
        if (classMatcher.find()) {
            lines.add(classMatcher.group(0).replace("{", "{").trim());
        } else {
            lines.add("public class " + className + " {");
        }

        // Injected fields (@Autowired / @Value annotations + field declaration)
        Matcher fieldMatcher = FIELD_PATTERN.matcher(source);
        while (fieldMatcher.find()) {
            // Include the @Autowired/@Value annotation above the field if present
            int fieldStart = fieldMatcher.start();
            String precedingText = source.substring(Math.max(0, fieldStart - 120), fieldStart);
            if (precedingText.contains("@Autowired") || precedingText.contains("@Value")
                    || precedingText.contains("@Inject") || precedingText.contains("final")) {
                lines.add("    " + fieldMatcher.group(0).trim());
            }
        }

        if (lines.size() > 2) lines.add("");

        // Public/protected method signatures only (no body)
        Matcher methodMatcher = METHOD_PATTERN.matcher(source);
        while (methodMatcher.find()) {
            String sig = methodMatcher.group(0)
                    .replaceAll("\\{\\s*$", "")
                    .trim();
            lines.add("    " + sig + " { ... }");
        }

        lines.add("}");

        String skeleton = String.join("\n", lines);
        // Hard cap at 1500 chars to stay safely within VRAM budget
        if (skeleton.length() > 1500) {
            skeleton = skeleton.substring(0, 1500) + "\n    // ... (truncated)\n}";
        }
        return skeleton;
    }

    private void deleteDirectory(Path dir) {
        if (dir == null) return;
        try {
            if (Files.exists(dir)) {
                Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }
        } catch (IOException e) {
            log.warn("Failed to delete temp dir {}: {}", dir, e.getMessage());
        }
    }
}
