package com.pfe.platform.msexecution.service;

import com.pfe.platform.msexecution.dto.request.UxEvaluationRequest;
import com.pfe.platform.msexecution.entity.UxEvaluation;
import com.pfe.platform.msexecution.entity.UxEvaluation.Platform;
import com.pfe.platform.msexecution.entity.UxEvaluation.Status;
import com.pfe.platform.msexecution.repository.UxEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class UxEvaluationService {

    private static final Duration EXECUTION_TIMEOUT = Duration.ofMinutes(5);
    private static final String TEMP_ROOT_DIR = "ux-evaluations";
    private static final String PERMANENT_SCREENSHOT_DIR = "ms-execution/ux-screenshots";
    private static final String GENERATED_PACKAGE = "com.pfe.platform.uxevaluations";
    private static final String GENERATED_CLASS_NAME = "UxEvaluationTest";
    private static final String RUNNER_PACKAGE = "com.pfe.platform.msexecution.uxruntime";
    private static final String RUNNER_CLASS_NAME = "UxEvaluationRunner";
    private static final String TEST_SUMMARY_MARKER = "TEST_SUMMARY:";
    private static final String PAGE_CONTENT_MARKER = "PAGE_CONTENT:";
    private static final AtomicReference<String> COMPILATION_CLASSPATH_CACHE = new AtomicReference<>();

    private final UxEvaluationRepository uxEvaluationRepository;
    private final LlmClient llmClient;
    private final LlmAnalysisService llmAnalysisService;

    @Transactional
    public UxEvaluation createEvaluation(UxEvaluationRequest req) {
        UxEvaluation evaluation = UxEvaluation.builder()
                .projectId(req.getProjectId())
                .platform(Platform.valueOf(req.getPlatform().toUpperCase()))
                .url(req.getUrl())
                .description(req.getDescription())
                .generatedScript(req.getGeneratedScript())
                .status(Status.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        return uxEvaluationRepository.save(evaluation);
    }

    @Async
    public void executeEvaluation(Long id) {
        log.info("UX evaluation {}: start", id);
        Optional<UxEvaluation> optional = uxEvaluationRepository.findById(id);
        if (optional.isEmpty()) {
            log.warn("UxEvaluation not found: {}", id);
            return;
        }

        UxEvaluation evaluation = optional.get();
        evaluation.setStatus(Status.RUNNING);
        evaluation.setExecutedAt(LocalDateTime.now());
        evaluation.setErrorMessage(null);
        uxEvaluationRepository.save(evaluation);

        Instant startedAt = Instant.now();
        Path workspace = null;
        Path wrapper = null;
        StringBuilder executionLogs = new StringBuilder();

        try {
            String generatedScript = evaluation.getGeneratedScript();
            if (generatedScript == null || generatedScript.isBlank()) {
                log.info("UX evaluation {}: generating script with LLM", id);
                generatedScript = generateUxScript(evaluation.getPlatform(), evaluation.getUrl(), evaluation.getDescription());
            }
            if (generatedScript == null || generatedScript.isBlank()) {
                log.error("UX evaluation {}: unable to generate script", id);
                failEvaluation(evaluation, startedAt, "Service IA indisponible", executionLogs.toString(), null, null);
                return;
            }

            log.info("UX evaluation {}: script ready ({} chars)", id, generatedScript.length());
            workspace = createTempMavenProject(id, generatedScript, evaluation.getUrl());
            log.info("UX evaluation {}: temporary Maven project created at {}", id, workspace.toAbsolutePath());

            wrapper = ensureMavenWrapper(workspace);
            log.info("UX evaluation {}: Maven wrapper ready at {}", id, wrapper.toAbsolutePath());

            ProcessRunResult execution = runMavenTest(workspace, evaluation.getUrl(), wrapper);
            appendProcessLogs(executionLogs, "MAVEN", execution.logs());
            log.info("UX evaluation {}: Maven finished with exitCode={} timedOut={}", id, execution.exitCode(), execution.timedOut());

            if (execution.timedOut()) {
                log.error("UX evaluation {}: Maven execution timed out", id);
                failEvaluation(evaluation, startedAt, "Timeout d'exécution", executionLogs.toString(), null, null);
                return;
            }

            if (execution.exitCode() != 0) {
                String failureMessage = (execution.logs() == null || execution.logs().isBlank())
                        ? "Compilation/exécution impossible"
                        : execution.logs();
                log.error("UX evaluation {}: Maven execution failed with exitCode={}", id, execution.exitCode());
                failEvaluation(evaluation, startedAt, failureMessage, executionLogs.toString(), null, null);
                return;
            }

            String logs = executionLogs.toString();
            String testSummary = extractMarkedSection(logs, TEST_SUMMARY_MARKER);
            String pageContent = extractMarkedSection(logs, PAGE_CONTENT_MARKER);
            log.info("UX evaluation {}: TEST_SUMMARY extracted -> {}", id, summarizeForLog(testSummary));
            log.info("UX evaluation {}: PAGE_CONTENT extracted -> {}", id, summarizeForLog(pageContent));

            evaluation.setTestSummary(testSummary);
            evaluation.setPageContent(pageContent);
            String aiAnalysis = null;
            if (testSummary != null && !testSummary.isBlank()) {
                try {
                    log.info("UX evaluation {}: running AI analysis", id);
                    aiAnalysis = llmAnalysisService.analyzeFunctionalTest(testSummary, pageContent, evaluation.getPlatform().name());
                    log.info("UX evaluation {}: AI analysis completed", id);
                } catch (Exception analysisEx) {
                    log.error("UX evaluation {}: AI analysis failed", id, analysisEx);
                }
            } else {
                log.info("UX evaluation {}: skipping AI analysis because TEST_SUMMARY is missing", id);
            }

            evaluation.setAiAnalysis(aiAnalysis);
            evaluation.setLogs(logs);
            evaluation.setScreenshotUrl(copyScreenshotIfPresent(workspace, evaluation.getId()));
            evaluation.setDurationMs(Duration.between(startedAt, Instant.now()).toMillis());
            evaluation.setStatus(Status.COMPLETED);
            evaluation.setExecutedAt(LocalDateTime.now());
            uxEvaluationRepository.save(evaluation);
            log.info("UX evaluation {}: completed successfully in {} ms", id, evaluation.getDurationMs());
        } catch (Exception ex) {
            log.error("Error executing UX evaluation {}", id, ex);
            failEvaluation(evaluation, startedAt, ex.getMessage() == null ? "Erreur inconnue" : ex.getMessage(), executionLogs.toString(), null, null);
        } finally {
            if (workspace != null) {
                try {
                    log.info("UX evaluation {}: cleaning temporary workspace {}", id, workspace.toAbsolutePath());
                    deleteRecursively(workspace);
                } catch (Exception cleanupEx) {
                    log.debug("Unable to cleanup UX workspace {}: {}", workspace, cleanupEx.getMessage());
                }
            }
        }
    }

    private Path createTempMavenProject(Long evaluationId, String script, String url) throws IOException {
        Path workspace = Path.of(System.getProperty("java.io.tmpdir"), TEMP_ROOT_DIR, "evaluation-" + evaluationId + "-" + System.currentTimeMillis());
        Path testDir = workspace.resolve("src/test/java/ux/evaluation");
        Files.createDirectories(testDir);
        Files.createDirectories(workspace.resolve("target"));

        String cleanedScript = cleanGeneratedScript(script, url);
        Path testFile = testDir.resolve(GENERATED_CLASS_NAME + ".java");
        Files.writeString(testFile, cleanedScript, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.info("UX evaluation {}: generated test written to {}", evaluationId, testFile.toAbsolutePath());

        Path pomFile = workspace.resolve("pom.xml");
        Files.writeString(pomFile, buildTempMavenPom(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.info("UX evaluation {}: pom.xml written to {}", evaluationId, pomFile.toAbsolutePath());

        return workspace;
    }

    private String cleanGeneratedScript(String script, String url) {
        String code = script == null ? "" : script.replace("```", "").trim();
        if (!code.contains("package ")) {
            code = "package ux.evaluation;\n\n" + code;
        }
        Pattern classPattern = Pattern.compile("public\\s+class\\s+([A-Za-z0-9_]+)");
        Matcher matcher = classPattern.matcher(code);
        if (matcher.find()) {
            String originalName = matcher.group(1);
            if (!GENERATED_CLASS_NAME.equals(originalName)) {
                code = code.replaceFirst("public\\s+class\\s+" + Pattern.quote(originalName), "public class " + GENERATED_CLASS_NAME);
                code = code.replaceAll("\\b" + Pattern.quote(originalName) + "\\b", GENERATED_CLASS_NAME);
            }
        } else if (!code.contains("class " + GENERATED_CLASS_NAME)) {
            code = code + "\n\npublic class " + GENERATED_CLASS_NAME + " {}";
        }
        if (url != null && !url.isBlank() && !code.contains("UX_URL")) {
            code = code + "\n// UX_URL=" + url;
        }
        return code;
    }

    private String buildTempMavenPom() {
        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.pfe.platform</groupId>
                    <artifactId>ux-evaluation-temp</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.seleniumhq.selenium</groupId>
                            <artifactId>htmlunit-driver</artifactId>
                            <version>4.13.0</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.testng</groupId>
                            <artifactId>testng</artifactId>
                            <version>7.9.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-surefire-plugin</artifactId>
                                <version>3.2.5</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;
    }

    private Path ensureMavenWrapper(Path workspace) throws IOException {
        Path moduleRoot = locateModuleRoot();
        if (moduleRoot == null) {
            throw new IOException("Unable to locate module root containing pom.xml");
        }

        Path sourceWrapper = findMavenWrapper(moduleRoot);
        if (sourceWrapper == null) {
            throw new IOException("Maven wrapper not found in module root");
        }

        copyIfExists(moduleRoot.resolve(".mvn"), workspace.resolve(".mvn"));

        Path targetWrapper = workspace.resolve(isWindows() ? "mvnw.cmd" : "mvnw");
        Path alternateWrapper = workspace.resolve(isWindows() ? "mvnw" : "mvnw.cmd");
        Files.copy(sourceWrapper, targetWrapper, StandardCopyOption.REPLACE_EXISTING);
        if (Files.exists(alternateWrapper)) {
            Files.delete(alternateWrapper);
        }
        return targetWrapper;
    }

    private void copyIfExists(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            return;
        }

        if (Files.isDirectory(source)) {
            try (var stream = Files.walk(source)) {
                List<Path> paths = stream.sorted(Comparator.naturalOrder()).toList();
                for (Path path : paths) {
                    Path destination = target.resolve(source.relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            return;
        }

        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private Path findMavenWrapper(Path startDir) {
        Path current = startDir.toAbsolutePath();
        while (current != null) {
            Path cmd = current.resolve("mvnw.cmd");
            if (Files.exists(cmd)) {
                return cmd;
            }
            Path sh = current.resolve("mvnw");
            if (Files.exists(sh)) {
                return sh;
            }
            current = current.getParent();
        }
        return null;
    }

    private ProcessRunResult runMavenTest(Path workspace, String url, Path wrapper) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(wrapper.toAbsolutePath().toString());
        command.add("test");
        command.add("-DUX_URL=" + safeValue(url));
        command.add("-q");
        log.info("UX evaluation Maven command: {}", String.join(" ", command));
        return runPlainProcess(command, workspace, EXECUTION_TIMEOUT.toMinutes(), TimeUnit.MINUTES);
    }

    private String generateUxScript(Platform platform, String url, String description) {
        String composedDescription = "Plateforme: " + platform.name() + "\n"
                + "Cible: " + (url == null ? "" : url) + "\n"
                + "Description: " + (description == null ? "" : description) + "\n"
                + "Le script doit afficher exactement '" + TEST_SUMMARY_MARKER + "' suivi d'un résumé concis en français. "
                + "Le script doit aussi afficher exactement '" + PAGE_CONTENT_MARKER + "' suivi du contenu de page observé ou extrait. "
                + (platform == Platform.WEB
                ? "Le script web doit utiliser exclusivement HtmlUnitDriver, lire l'URL via System.getProperty(\"UX_URL\"), prendre une capture d'écran après chaque action importante et sauvegarder les images dans System.getProperty(\"java.io.tmpdir\")."
                : "Le script mobile doit utiliser exclusivement AppiumDriver, lire le package via System.getProperty(\"UX_PACKAGE\") et l'activité via System.getProperty(\"UX_ACTIVITY\"), prendre une capture d'écran après chaque action et sauvegarder les images dans System.getProperty(\"java.io.tmpdir\").");

        try {
            return llmClient.generateFunctionalTestCode(platform.name(), composedDescription);
        } catch (Exception ex) {
            log.warn("LLM UX generation failed: {}", ex.getMessage());
            return null;
        }
    }

    private Path prepareWorkspace(Long evaluationId) throws IOException {
        Path workspace = Path.of(System.getProperty("java.io.tmpdir"), TEMP_ROOT_DIR, "evaluation-" + evaluationId + "-" + System.currentTimeMillis());
        Files.createDirectories(workspace.resolve("src/main/java/" + GENERATED_PACKAGE.replace('.', '/')));
        Files.createDirectories(workspace.resolve("src/main/java/" + RUNNER_PACKAGE.replace('.', '/')));
        Files.createDirectories(workspace.resolve("target/classes"));
        return workspace;
    }

    private Path writeGeneratedTest(Path workspace, String generatedScript, Platform platform, String url) throws IOException {
        String normalized = normalizeGeneratedScript(generatedScript, platform, url);
        Path testFile = workspace.resolve("src/main/java/" + GENERATED_PACKAGE.replace('.', '/') + "/" + GENERATED_CLASS_NAME + ".java");
        Files.writeString(testFile, normalized, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return testFile;
    }

    private Path writeRunner(Path workspace) throws IOException {
        Path runnerFile = workspace.resolve("src/main/java/" + RUNNER_PACKAGE.replace('.', '/') + "/" + RUNNER_CLASS_NAME + ".java");
        Files.createDirectories(runnerFile.getParent());
        Files.writeString(runnerFile, buildRunnerSource(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return runnerFile;
    }

    private String buildRunnerSource() {
        return """
                package %s;

                import java.lang.annotation.Annotation;
                import java.lang.reflect.InvocationTargetException;
                import java.lang.reflect.Method;
                import java.lang.reflect.Modifier;
                import java.util.ArrayList;
                import java.util.Arrays;
                import java.util.Comparator;
                import java.util.List;

                public final class %s {
                    private %s() {
                    }

                    public static void main(String[] args) throws Exception {
                        if (args.length == 0 || args[0] == null || args[0].isBlank()) {
                            throw new IllegalArgumentException("Missing generated class name");
                        }

                        String generatedClassName = args[0];
                        Class<?> generatedClass = Class.forName(generatedClassName);
                        System.out.println("UX_RUNNER: loaded " + generatedClassName);

                        Method mainMethod = findMain(generatedClass);
                        if (mainMethod != null) {
                            System.out.println("UX_RUNNER: invoking main");
                            mainMethod.invoke(null, (Object) new String[0]);
                            System.out.println("UX_RUNNER: main completed");
                            return;
                        }

                        List<Method> runnableMethods = findRunnableMethods(generatedClass);
                        if (runnableMethods.isEmpty()) {
                            throw new IllegalStateException("No runnable method found in " + generatedClassName);
                        }

                        Object instance = instantiateIfNeeded(generatedClass, runnableMethods);
                        for (Method method : runnableMethods) {
                            System.out.println("UX_RUNNER: invoking " + method.getName());
                            invokeMethod(method, instance);
                        }
                        System.out.println("UX_RUNNER: completed " + runnableMethods.size() + " method(s)");
                    }

                    private static Method findMain(Class<?> generatedClass) {
                        try {
                            Method method = generatedClass.getMethod("main", String[].class);
                            return Modifier.isStatic(method.getModifiers()) ? method : null;
                        } catch (NoSuchMethodException ex) {
                            return null;
                        }
                    }

                    private static List<Method> findRunnableMethods(Class<?> generatedClass) {
                        List<Method> methods = new ArrayList<>();
                        Arrays.stream(generatedClass.getDeclaredMethods())
                                .filter(method -> !method.isSynthetic() && !method.isBridge())
                                .filter(method -> method.getParameterCount() == 0)
                                .filter(method -> isAnnotatedTest(method) || isNamedRunnable(method))
                                .sorted(Comparator.comparing(Method::getName))
                                .forEach(method -> {
                                    method.setAccessible(true);
                                    methods.add(method);
                                });
                        return methods;
                    }

                    private static boolean isAnnotatedTest(Method method) {
                        for (Annotation annotation : method.getDeclaredAnnotations()) {
                            String simpleName = annotation.annotationType().getSimpleName();
                            if ("Test".equals(simpleName) || simpleName.endsWith("Test")) {
                                return true;
                            }
                        }
                        return false;
                    }

                    private static boolean isNamedRunnable(Method method) {
                        String name = method.getName().toLowerCase();
                        return name.equals("run") || name.equals("execute") || name.startsWith("test");
                    }

                    private static Object instantiateIfNeeded(Class<?> generatedClass, List<Method> methods) throws Exception {
                        boolean requiresInstance = methods.stream().anyMatch(method -> !Modifier.isStatic(method.getModifiers()));
                        if (!requiresInstance) {
                            return null;
                        }
                        var constructor = generatedClass.getDeclaredConstructor();
                        constructor.setAccessible(true);
                        return constructor.newInstance();
                    }

                    private static void invokeMethod(Method method, Object instance) throws Exception {
                        try {
                            method.invoke(Modifier.isStatic(method.getModifiers()) ? null : instance);
                        } catch (InvocationTargetException ex) {
                            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                            if (cause instanceof Exception exception) {
                                throw exception;
                            }
                            if (cause instanceof Error error) {
                                throw error;
                            }
                            throw new RuntimeException(cause);
                        }
                    }
                }
                """.formatted(RUNNER_PACKAGE, RUNNER_CLASS_NAME, RUNNER_CLASS_NAME);
    }

    private ProcessRunResult compileSources(Path workspace, List<Path> sources) throws IOException, InterruptedException {
        Path classesDir = Files.createDirectories(workspace.resolve("target/classes"));
        List<String> args = new ArrayList<>();
        args.add("-encoding");
        args.add("UTF-8");
        args.add("-classpath");
        args.add(resolveCompilationClasspath());
        args.add("-d");
        args.add(classesDir.toString());
        for (Path source : sources) {
            args.add(source.toString());
        }
        return runJavaTool("javac", args, workspace, "javac");
    }

    private ProcessRunResult runGeneratedTest(Path workspace, UxEvaluation evaluation) throws IOException, InterruptedException {
        List<String> args = new ArrayList<>();
        args.add("-classpath");
        args.add(buildRuntimeClasspath(workspace));
        args.add("-Djava.io.tmpdir=" + workspace.toAbsolutePath());
        args.add("-DUX_URL=" + safeValue(evaluation.getUrl()));
        args.add("-DUX_PACKAGE=" + safeValue(evaluation.getUrl()));
        args.add("-DUX_ACTIVITY=" + safeValue(resolveMobileActivity(evaluation.getDescription())));
        args.add("-DUX_PLATFORM=" + evaluation.getPlatform().name());
        args.add(RUNNER_PACKAGE + "." + RUNNER_CLASS_NAME);
        args.add(GENERATED_PACKAGE + "." + GENERATED_CLASS_NAME);
        return runJavaTool("java", args, workspace, "java");
    }

    private String buildRuntimeClasspath(Path workspace) {
        return workspace.resolve("target/classes") + File.pathSeparator + resolveCompilationClasspath();
    }

    private String resolveCompilationClasspath() {
        String cached = COMPILATION_CLASSPATH_CACHE.get();
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        String resolved = resolveMavenClasspath();
        if (resolved == null || resolved.isBlank()) {
            resolved = System.getProperty("java.class.path", "");
        } else {
            String runtimeClasspath = System.getProperty("java.class.path", "");
            if (runtimeClasspath != null && !runtimeClasspath.isBlank()) {
                resolved = resolved + File.pathSeparator + runtimeClasspath;
            }
        }

        if (resolved == null) {
            resolved = "";
        }

        COMPILATION_CLASSPATH_CACHE.compareAndSet(null, resolved);
        return COMPILATION_CLASSPATH_CACHE.get();
    }

    private String resolveMavenClasspath() {
        Path moduleRoot = locateModuleRoot();
        if (moduleRoot == null) {
            return null;
        }

        try {
            Path outputFile = Files.createTempFile("ux-evaluation-classpath", ".txt");
            List<String> command = new ArrayList<>();
            if (isWindows() && Files.exists(moduleRoot.resolve("mvnw.cmd"))) {
                command.add("cmd");
                command.add("/c");
                command.add(moduleRoot.resolve("mvnw.cmd").toString());
            } else if (!isWindows() && Files.exists(moduleRoot.resolve("mvnw"))) {
                command.add(moduleRoot.resolve("mvnw").toString());
            } else {
                command.add("mvn");
            }
            command.add("-q");
            command.add("-DincludeScope=test");
            command.add("-Dmdep.outputFile=" + outputFile.toAbsolutePath());
            command.add("dependency:build-classpath");

            ProcessRunResult result = runPlainProcess(command, moduleRoot, 2, TimeUnit.MINUTES);
            if (result.exitCode() != 0) {
                log.warn("Unable to resolve Maven classpath for UX evaluation: {}", summarizeForLog(result.logs()));
                return null;
            }

            String classpath = Files.readString(outputFile, StandardCharsets.UTF_8).trim();
            return classpath.isBlank() ? null : classpath;
        } catch (Exception ex) {
            log.warn("Unable to resolve Maven classpath for UX evaluation", ex);
            return null;
        }
    }

    private Path locateModuleRoot() {
        Path current = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private ProcessRunResult runJavaTool(String tool, List<String> args, Path workspace, String stage) throws IOException, InterruptedException {
        Path argFile = workspace.resolve("target").resolve(stage + "-args.txt");
        Files.createDirectories(argFile.getParent());
        Files.writeString(argFile, formatArgFile(args), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        List<String> command = List.of(tool, "@" + argFile.toAbsolutePath());
        return runPlainProcess(command, workspace, EXECUTION_TIMEOUT.toMinutes(), TimeUnit.MINUTES);
    }

    private ProcessRunResult runPlainProcess(List<String> command, Path workingDirectory, long timeout, TimeUnit unit) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        StringBuilder logs = new StringBuilder();
        AtomicReference<IOException> failure = new AtomicReference<>();
        Thread collector = new Thread(() -> collectLogs(process, logs, failure), "ux-evaluation-process-collector");
        collector.setDaemon(true);
        collector.start();

        boolean finished = process.waitFor(timeout, unit);
        if (!finished) {
            process.destroyForcibly();
            collector.join(TimeUnit.SECONDS.toMillis(5));
            return ProcessRunResult.timeout(logs.toString());
        }

        collector.join(TimeUnit.SECONDS.toMillis(5));
        if (failure.get() != null) {
            throw failure.get();
        }

        return ProcessRunResult.finished(process.exitValue(), logs.toString());
    }

    private String formatArgFile(List<String> args) {
        return args.stream()
                .map(this::escapeArgFileValue)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("") + System.lineSeparator();
    }

    private String escapeArgFileValue(String value) {
        if (value == null || value.isEmpty()) {
            return "\"\"";
        }
        if (value.indexOf(' ') < 0 && value.indexOf('\t') < 0 && value.indexOf('"') < 0) {
            return value;
        }
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private void appendProcessLogs(StringBuilder target, String stage, String logs) {
        target.append(System.lineSeparator())
                .append("=== ")
                .append(stage)
                .append(" ===")
                .append(System.lineSeparator());
        if (logs != null && !logs.isBlank()) {
            target.append(logs.trim()).append(System.lineSeparator());
        }
    }

    private String summarizeForLog(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private String normalizeGeneratedScript(String generatedScript, Platform platform, String url) {
        String code = generatedScript.replaceAll("(?i)```java\\s*", "").replaceAll("```", "").trim();

        if (!code.contains("package ")) {
            code = "package " + GENERATED_PACKAGE + ";\n\n" + code;
        }

        Pattern classPattern = Pattern.compile("public\\s+class\\s+([A-Za-z0-9_]+)");
        Matcher matcher = classPattern.matcher(code);
        if (matcher.find()) {
            String originalName = matcher.group(1);
            if (!GENERATED_CLASS_NAME.equals(originalName)) {
                code = code.replaceFirst("public\\s+class\\s+" + Pattern.quote(originalName), "public class " + GENERATED_CLASS_NAME);
                code = code.replaceAll("\\b" + Pattern.quote(originalName) + "\\b", GENERATED_CLASS_NAME);
            }
        } else if (!code.contains("class " + GENERATED_CLASS_NAME)) {
            code = code + "\n\npublic class " + GENERATED_CLASS_NAME + " {}";
        }

        if (platform == Platform.WEB) {
            code = ensureContains(code, "System.getProperty(\"UX_URL\")", "// UX_URL=" + (url == null ? "" : url));
        } else {
            code = ensureContains(code, "System.getProperty(\"UX_PACKAGE\")", "// UX_PACKAGE=" + (url == null ? "" : url));
        }

        return code;
    }

    private String ensureContains(String code, String needle, String fallbackComment) {
        return code.contains(needle) ? code : code + "\n" + fallbackComment;
    }

    private Path writePom(Path workspace, Platform platform) throws IOException {
        Path pomFile = workspace.resolve("pom.xml");
        Files.writeString(pomFile, buildPom(platform), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return pomFile;
    }

    private String buildPom(Platform platform) {
        String platformSpecificDependency = platform == Platform.WEB
                ? """
                  <dependency>
                      <groupId>org.htmlunit</groupId>
                      <artifactId>htmlunit</artifactId>
                      <version>4.3.0</version>
                      <scope>test</scope>
                  </dependency>
                  """
                : """
                  <dependency>
                      <groupId>io.appium</groupId>
                      <artifactId>java-client</artifactId>
                      <version>9.2.2</version>
                      <scope>test</scope>
                  </dependency>
                  """;

        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.pfe.platform</groupId>
                    <artifactId>ux-evaluation-%s</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.testng</groupId>
                            <artifactId>testng</artifactId>
                            <version>7.10.2</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.seleniumhq.selenium</groupId>
                            <artifactId>selenium-java</artifactId>
                            <version>4.23.1</version>
                            <scope>test</scope>
                        </dependency>
                        %s
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-surefire-plugin</artifactId>
                                <version>3.2.5</version>
                                <configuration>
                                    <useSystemClassLoader>false</useSystemClassLoader>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(platform.name().toLowerCase(), platformSpecificDependency);
    }

    private ProcessRunResult runMavenTest(Path workspace, UxEvaluation evaluation) throws IOException, InterruptedException {
        String tempDir = workspace.toAbsolutePath().toString();
        String activity = resolveMobileActivity(evaluation.getDescription());
        List<String> command = evaluation.getPlatform() == Platform.WEB
            ? List.of(
                "mvn",
                "-q",
                "-Djava.io.tmpdir=" + tempDir,
                "-DUX_URL=" + safeValue(evaluation.getUrl()),
                "-Dtest=" + GENERATED_PACKAGE + "." + GENERATED_CLASS_NAME,
                "test")
            : List.of(
                "mvn",
                "-q",
                "-Djava.io.tmpdir=" + tempDir,
                "-DUX_PACKAGE=" + safeValue(evaluation.getUrl()),
                "-DUX_ACTIVITY=" + activity,
                "-Dtest=" + GENERATED_PACKAGE + "." + GENERATED_CLASS_NAME,
                "test");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workspace.toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("UX_URL", evaluation.getUrl() == null ? "" : evaluation.getUrl());
        builder.environment().put("UX_PACKAGE", evaluation.getUrl() == null ? "" : evaluation.getUrl());
        builder.environment().put("UX_ACTIVITY", activity);
        builder.environment().put("UX_PLATFORM", evaluation.getPlatform().name());
        builder.environment().put("MAVEN_OPTS", "-Dfile.encoding=UTF-8");

        Process process = builder.start();
        StringBuilder logs = new StringBuilder();
        AtomicReference<IOException> failure = new AtomicReference<>();
        Thread collector = new Thread(() -> collectLogs(process, logs, failure), "ux-evaluation-log-collector");
        collector.setDaemon(true);
        collector.start();

        boolean finished = process.waitFor(EXECUTION_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            collector.join(5000);
            return ProcessRunResult.timeout(logs.toString());
        }

        collector.join(5000);
        if (failure.get() != null) {
            throw failure.get();
        }

        return ProcessRunResult.finished(process.exitValue(), logs.toString());
    }

    private void collectLogs(Process process, StringBuilder logs, AtomicReference<IOException> failure) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (logs) {
                    logs.append(line).append(System.lineSeparator());
                }
            }
        } catch (IOException ex) {
            failure.set(ex);
        }
    }

    private void failEvaluation(UxEvaluation evaluation, Instant startedAt, String errorMessage, String logs, String screenshotUrl, String fallbackScreenshotUrl) {
        evaluation.setStatus(Status.FAILED);
        evaluation.setErrorMessage(errorMessage == null || errorMessage.isBlank() ? "Service IA indisponible" : errorMessage);
        evaluation.setTestSummary(extractMarkedSection(logs, TEST_SUMMARY_MARKER));
        evaluation.setPageContent(extractMarkedSection(logs, PAGE_CONTENT_MARKER));
        evaluation.setAiAnalysis(null);
        evaluation.setLogs(logs);
        evaluation.setScreenshotUrl(screenshotUrl != null ? screenshotUrl : fallbackScreenshotUrl);
        evaluation.setDurationMs(Duration.between(startedAt, Instant.now()).toMillis());
        evaluation.setExecutedAt(LocalDateTime.now());
        uxEvaluationRepository.save(evaluation);
    }

    public Path resolveScreenshotPath(Long id) {
        Optional<UxEvaluation> optional = uxEvaluationRepository.findById(id);
        if (optional.isEmpty()) {
            return null;
        }
        String screenshotUrl = optional.get().getScreenshotUrl();
        if (screenshotUrl == null || screenshotUrl.isBlank()) {
            return null;
        }
        Path path = toPath(screenshotUrl);
        return Files.exists(path) && Files.isRegularFile(path) ? path : null;
    }

    public String extractUxSummary(String logs) {
        return extractMarkedSection(logs, TEST_SUMMARY_MARKER);
    }

    private String extractMarkedSection(String logs, String marker) {
        if (logs == null || logs.isBlank()) {
            return null;
        }
        Pattern pattern = Pattern.compile(Pattern.quote(marker) + "\\s*(.+)");
        Matcher matcher = pattern.matcher(logs);
        if (!matcher.find()) {
            return null;
        }
        String summary = matcher.group(1);
        return summary == null ? null : summary.trim();
    }

    private String findScreenshotUrl(Path workspace, String logs) {
        if (workspace == null || !Files.exists(workspace)) {
            return extractScreenshotFromLogs(logs);
        }
        try (var stream = Files.walk(workspace)) {
            Optional<Path> screenshot = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
                    })
                    .findFirst();
            return screenshot.map(path -> path.toUri().toString()).orElseGet(() -> extractScreenshotFromLogs(logs));
        } catch (IOException ex) {
            return extractScreenshotFromLogs(logs);
        }
    }

    private String extractScreenshotFromLogs(String logs) {
        if (logs == null || logs.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("(?im)^SCREENSHOT_URL:\\s*(.+)$").matcher(logs);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        matcher = Pattern.compile("(?im)^SCREENSHOT_PATH:\\s*(.+)$").matcher(logs);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String copyScreenshotIfPresent(Path workspace, Long evaluationId) {
        Path screenshot = findScreenshot(workspace);
        if (screenshot == null) {
            return null;
        }

        try {
            Path permanentDir = Path.of(System.getProperty("java.io.tmpdir"), PERMANENT_SCREENSHOT_DIR);
            Files.createDirectories(permanentDir);
            Path destination = permanentDir.resolve(evaluationId + ".png");
            Files.copy(screenshot, destination, StandardCopyOption.REPLACE_EXISTING);
            return destination.toAbsolutePath().toString();
        } catch (IOException ex) {
            log.warn("Unable to persist UX screenshot for evaluation {}: {}", evaluationId, ex.getMessage());
            return screenshot.toAbsolutePath().toString();
        }
    }

    private Path findScreenshot(Path workspace) {
        if (workspace == null || !Files.exists(workspace)) {
            return null;
        }

        try (var stream = Files.walk(workspace)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".png"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException ex) {
            log.debug("Unable to scan UX workspace for screenshots: {}", ex.getMessage());
            return null;
        }
    }

    private Path toPath(String value) {
        try {
            if (value.startsWith("file:")) {
                return Path.of(URI.create(value));
            }
        } catch (Exception ignored) {
            // fall through to Path.of below
        }
        return Path.of(value);
    }

    private String resolveMobileActivity(String description) {
        if (description == null || description.isBlank()) {
            return "MainActivity";
        }
        Matcher matcher = Pattern.compile("(?i)(?:activity|activity=|activity:)\\s*([A-Za-z0-9_.$]+)").matcher(description);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "MainActivity";
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
    }

    private record ProcessRunResult(int exitCode, String logs, boolean timedOut) {
        static ProcessRunResult finished(int exitCode, String logs) {
            return new ProcessRunResult(exitCode, logs, false);
        }

        static ProcessRunResult timeout(String logs) {
            return new ProcessRunResult(-1, logs, true);
        }
    }
}
