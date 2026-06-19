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
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s+[\\w.]+\\s*;");

    public record ExtractionResult(String skeleton, String fileTree, String dependencySources) {}

    public String extractSkeleton(String gitRepoUrl, String branch, String modulePath, String className) {
        ExtractionResult result = extractSkeletonWithTree(gitRepoUrl, branch, modulePath, className);
        return result != null ? result.skeleton() : null;
    }

    public String extractFullSource(String gitRepoUrl, String branch, String modulePath, String className) {
        return extractSkeleton(gitRepoUrl, branch, modulePath, className);
    }

    public ExtractionResult extractSkeletonWithTree(String gitRepoUrl, String branch, String modulePath, String className) {
        if (gitRepoUrl == null || gitRepoUrl.isBlank() || className == null || className.isBlank()) {
            return null;
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("skeleton-");
            log.info("Cloning {} @ {} for source extraction", gitRepoUrl, branch);

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

            String fileTree = buildFileTree(mainJava);

            Path classFile = findClassFile(mainJava, className);
            if (classFile == null) {
                log.warn("Class {} not found under {}", className, mainJava);
                return new ExtractionResult(null, fileTree, null);
            }

            String fullSource = Files.readString(classFile, StandardCharsets.UTF_8);
            log.info("[SkeletonExtractor] Found class file: {} ({} chars)", classFile, fullSource.length());
            String cleanedSource = cleanSource(fullSource);
            log.info("[SkeletonExtractor] Cleaned source: {} chars", cleanedSource.length());
            String depSources = extractDependencySources(fullSource, mainJava);
            log.info("[SkeletonExtractor] Dependencies extracted: {} chars, fileTree: {} chars",
                    depSources != null ? depSources.length() : 0,
                    fileTree != null ? fileTree.length() : 0);
            return new ExtractionResult(cleanedSource, fileTree, depSources);

        } catch (Exception e) {
            log.error("Source extraction failed for class {} in {}: {}", className, gitRepoUrl, e.getMessage());
            return null;
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private String cleanSource(String source) {
        return source
                .replaceAll("(?m)^\\s*@Getter\\s*$", "")
                .replaceAll("(?m)^\\s*@Setter\\s*$", "")
                .replaceAll("(?m)^\\s*@NoArgsConstructor\\s*$", "")
                .replaceAll("(?m)^\\s*@AllArgsConstructor\\s*$", "")
                .replaceAll("(?m)^\\s*@RequiredArgsConstructor\\s*$", "")
                .replaceAll("(?m)^\\s*@Builder\\s*$", "")
                .replaceAll("(?m)^\\s*@Slf4j\\s*$", "")
                .replaceAll("(?m)^\\s*@Service\\s*$", "")
                .replaceAll("(?m)^\\s*@Component\\s*$", "")
                .replaceAll("(?m)^\\s*@Repository\\s*$", "")
                .replaceAll("(?m)^\\s*@RestController\\s*$", "")
                .replaceAll("(?m)^\\s*import\\s+lombok\\..*?;\\s*$", "")
                .replaceAll("(?m)^\\s*import\\s+jakarta\\.persistence\\..*?;\\s*$", "")
                .replaceAll("(?m)^\\s*import\\s+org\\.springframework\\.stereotype\\..*?;\\s*$", "")
                .replaceAll("(?m)(^\\s*\\n){3,}", "\n\n")
                .trim();
    }

    private String buildFileTree(Path mainJava) {
        try (Stream<Path> stream = Files.walk(mainJava)) {
            List<String> javaFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(p -> mainJava.relativize(p).toString().replace("\\", "/"))
                    .sorted()
                    .collect(Collectors.toList());

            if (javaFiles.isEmpty()) return null;

            StringBuilder tree = new StringBuilder();
            for (String file : javaFiles) {
                String packagePath = file.replace("/", ".").replace(".java", "");
                tree.append(packagePath).append("\n");
            }
            return tree.toString().trim();
        } catch (Exception e) {
            log.warn("Failed to build file tree: {}", e.getMessage());
            return null;
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

        Matcher pkgMatcher = PACKAGE_PATTERN.matcher(source);
        if (pkgMatcher.find()) {
            lines.add("package " + pkgMatcher.group(1) + ";");
            lines.add("");
        }

        Matcher importMatcher = IMPORT_PATTERN.matcher(source);
        while (importMatcher.find()) {
            String imp = importMatcher.group(0).trim();
            if (!imp.contains("lombok") && !imp.contains("jakarta.persistence")
                    && !imp.contains("javax.persistence") && !imp.contains("org.springframework.stereotype")) {
                lines.add(imp);
            }
        }
        lines.add("");

        Matcher classMatcher = CLASS_PATTERN.matcher(source);
        if (classMatcher.find()) {
            lines.add(classMatcher.group(0).replace("{", "{").trim());
        } else {
            lines.add("public class " + className + " {");
        }

        Matcher fieldMatcher = FIELD_PATTERN.matcher(source);
        while (fieldMatcher.find()) {
            int fieldStart = fieldMatcher.start();
            String precedingText = source.substring(Math.max(0, fieldStart - 120), fieldStart);
            if (precedingText.contains("@Autowired") || precedingText.contains("@Value")
                    || precedingText.contains("@Inject") || precedingText.contains("final")) {
                lines.add("    " + fieldMatcher.group(0).trim());
            }
        }

        if (lines.size() > 2) lines.add("");

        Matcher methodMatcher = METHOD_PATTERN.matcher(source);
        while (methodMatcher.find()) {
            String sig = methodMatcher.group(0)
                    .replaceAll("\\{\\s*$", "")
                    .trim();
            lines.add("    " + sig + " { ... }");
        }

        lines.add("}");

        return String.join("\n", lines);
    }

    private static final Pattern PROJECT_IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s+((?!java\\.|javax\\.|jakarta\\.|org\\.springframework\\.|org\\.mockito\\.|org\\.testng\\.|org\\.slf4j\\.|org\\.hibernate\\.|lombok\\.|org\\.apache\\.)[\\w.]+)\\s*;");

    private String extractDependencySources(String mainClassSource, Path mainJava) {
        Matcher matcher = PROJECT_IMPORT_PATTERN.matcher(mainClassSource);
        StringBuilder deps = new StringBuilder();
        int count = 0;
        int maxDeps = 15;

        while (matcher.find() && count < maxDeps) {
            String fqcn = matcher.group(1).trim();
            String relativePath = fqcn.replace('.', '/') + ".java";
            Path depFile = mainJava.resolve(relativePath);

            if (!Files.isRegularFile(depFile)) continue;

            try {
                String depSource = Files.readString(depFile, StandardCharsets.UTF_8);
                String className = fqcn.substring(fqcn.lastIndexOf('.') + 1);
                String cleaned = buildDependencySkeleton(depSource, className);
                if (cleaned != null && !cleaned.isBlank()) {
                    deps.append("--- ").append(fqcn).append(" ---\n");
                    deps.append(cleaned).append("\n\n");
                    count++;
                }
            } catch (IOException e) {
                log.debug("Could not read dependency {}: {}", fqcn, e.getMessage());
            }
        }

        return deps.isEmpty() ? null : deps.toString().trim();
    }

    private String buildDependencySkeleton(String source, String className) {
        List<String> lines = new ArrayList<>();

        Matcher pkgMatcher = PACKAGE_PATTERN.matcher(source);
        if (pkgMatcher.find()) {
            lines.add("package " + pkgMatcher.group(1) + ";");
        }

        Matcher classMatcher = CLASS_PATTERN.matcher(source);
        if (classMatcher.find()) {
            lines.add(classMatcher.group(0).replace("{", "{").trim());
        } else {
            lines.add("public class " + className + " {");
        }

        Matcher fieldMatcher = FIELD_PATTERN.matcher(source);
        while (fieldMatcher.find()) {
            lines.add("    " + fieldMatcher.group(0).trim());
        }

        if (lines.size() > 2) lines.add("");

        Matcher methodMatcher = METHOD_PATTERN.matcher(source);
        while (methodMatcher.find()) {
            String sig = methodMatcher.group(0).replaceAll("\\{\\s*$", "").trim();
            lines.add("    " + sig + ";");
        }

        if (source.contains("enum ")) {
            Pattern enumValues = Pattern.compile("(?m)^\\s*([A-Z_]+)\\s*[,;(]");
            Matcher enumMatcher = enumValues.matcher(source);
            List<String> vals = new ArrayList<>();
            while (enumMatcher.find()) {
                vals.add(enumMatcher.group(1));
            }
            if (!vals.isEmpty()) {
                lines.add("    // enum values: " + String.join(", ", vals));
            }
        }

        lines.add("}");
        return String.join("\n", lines);
    }

    public record DtoFieldInfo(String name, String type, boolean required, List<String> enumValues) {}

    public List<DtoFieldInfo> extractDtoFields(String gitRepoUrl, String branch, String modulePath, String className) {
        if (gitRepoUrl == null || gitRepoUrl.isBlank() || className == null || className.isBlank()) {
            return List.of();
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("dto-fields-");
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
            if (!Files.isDirectory(mainJava)) mainJava = searchRoot;

            Path classFile = findClassFile(mainJava, className);
            if (classFile == null) {
                log.warn("[extractDtoFields] Class {} not found", className);
                return List.of();
            }

            String source = Files.readString(classFile, StandardCharsets.UTF_8);
            String outerClassSource = extractOuterClassFields(source, className);
            List<DtoFieldInfo> fields = new ArrayList<>();

            Matcher fieldMatcher = FIELD_PATTERN.matcher(outerClassSource);
            while (fieldMatcher.find()) {
                String fullMatch = fieldMatcher.group(0).trim();
                String fieldName = fieldMatcher.group(2);
                String rawType = extractFieldType(fullMatch);

                if (rawType.startsWith("List<") || rawType.startsWith("Set<") || rawType.startsWith("Map<")) {
                    continue;
                }

                List<String> enumValues = List.of();
                if (isLikelyEnum(source, rawType)) {
                    enumValues = extractEnumValuesFromSource(mainJava, rawType, source);
                }

                boolean required = !rawType.equals("Boolean");
                fields.add(new DtoFieldInfo(fieldName, mapJavaTypeToSimple(rawType), required, enumValues));
            }

            log.info("[extractDtoFields] Extracted {} fields from {}", fields.size(), className);
            return fields;

        } catch (Exception e) {
            log.error("[extractDtoFields] Failed for {} in {}: {}", className, gitRepoUrl, e.getMessage());
            return List.of();
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private String extractOuterClassFields(String source, String className) {
        Pattern outerClass = Pattern.compile(
                "(?s)(public\\s+)?(abstract\\s+)?(class|interface)\\s+" + Pattern.quote(className) + "[^{]*\\{(.*)$");
        Matcher m = outerClass.matcher(source);
        if (!m.find()) return source;

        String body = m.group(4);
        int depth = 1;
        int endPos = 0;
        for (int i = 0; i < body.length() && depth > 0; i++) {
            char c = body.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            if (depth == 0) { endPos = i; break; }
        }

        String outerBody = body.substring(0, endPos);
        Pattern innerClass = Pattern.compile("(?s)(public|private|protected)?\\s*(static\\s+)?(class|interface|enum)\\s+\\w+[^{]*\\{");
        Matcher inner = innerClass.matcher(outerBody);
        if (inner.find()) {
            outerBody = outerBody.substring(0, inner.start());
        }
        return outerBody;
    }

    private String extractFieldType(String fieldDeclaration) {
        String withoutAccess = fieldDeclaration
                .replaceFirst("^\\s*(private|protected|public)\\s+", "")
                .replaceFirst("\\s+\\w+\\s*;\\s*$", "")
                .trim();
        return withoutAccess;
    }

    private String mapJavaTypeToSimple(String javaType) {
        if (javaType == null) return "string";
        return switch (javaType) {
            case "int", "Integer", "long", "Long", "short", "Short" -> "integer";
            case "double", "Double", "float", "Float", "BigDecimal" -> "number";
            case "boolean", "Boolean" -> "boolean";
            case "String" -> "string";
            default -> {
                if (javaType.startsWith("List<") || javaType.startsWith("Set<")) yield "array";
                yield "string";
            }
        };
    }

    private boolean isLikelyEnum(String source, String typeName) {
        if (typeName == null || typeName.isEmpty()) return false;
        if (typeName.startsWith("List<") || typeName.startsWith("Set<") || typeName.startsWith("Map<")) return false;
        if (List.of("String","Integer","Long","Double","Float","Boolean","int","long","double","float","boolean",
                "Short","Byte","BigDecimal","LocalDateTime","LocalDate","Date").contains(typeName)) return false;
        Pattern innerEnum = Pattern.compile("enum\\s+" + Pattern.quote(typeName) + "\\s*\\{");
        return innerEnum.matcher(source).find();
    }

    private List<String> extractEnumValuesFromSource(Path mainJava, String enumTypeName, String containingSource) {
        Pattern enumBlock = Pattern.compile("enum\\s+" + Pattern.quote(enumTypeName) + "\\s*\\{([^}]*)\\}");
        Matcher m = enumBlock.matcher(containingSource);
        if (m.find()) {
            return parseEnumConstants(m.group(1));
        }
        Path enumFile = null;
        try { enumFile = findClassFile(mainJava, enumTypeName); } catch (IOException ignored) {}
        if (enumFile != null) {
            try {
                String enumSource = Files.readString(enumFile, StandardCharsets.UTF_8);
                Matcher em = enumBlock.matcher(enumSource);
                if (em.find()) return parseEnumConstants(em.group(1));
                Pattern simpleEnum = Pattern.compile("(?m)^\\s*([A-Z_]+)\\s*[,;(]");
                Matcher sem = simpleEnum.matcher(enumSource);
                List<String> vals = new ArrayList<>();
                while (sem.find()) vals.add(sem.group(1));
                return vals;
            } catch (IOException ignored) {}
        }
        return List.of();
    }

    private List<String> parseEnumConstants(String enumBody) {
        Pattern p = Pattern.compile("([A-Z_]+)");
        Matcher m = p.matcher(enumBody);
        List<String> vals = new ArrayList<>();
        while (m.find()) vals.add(m.group(1));
        return vals;
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
