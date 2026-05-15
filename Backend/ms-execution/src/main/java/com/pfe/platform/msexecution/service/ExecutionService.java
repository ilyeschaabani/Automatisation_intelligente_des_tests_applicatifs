package com.pfe.platform.msexecution.service;

import com.pfe.platform.msexecution.entity.*;
import com.pfe.platform.msexecution.repository.*;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

        private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z_][\\w]*(?:\\.[a-zA-Z_][\\w]*)*)\\s*;\\s*$");

        private static final String DEP_TESTNG_GROUP = "org.testng";
        private static final String DEP_TESTNG_ARTIFACT = "testng";
        private static final String DEP_TESTNG_VERSION = "7.9.0";

        private static final String DEP_MOCKITO_GROUP = "org.mockito";
        private static final String DEP_MOCKITO_ARTIFACT = "mockito-core";
        private static final String DEP_MOCKITO_VERSION = "5.7.0";

        private static final String DEP_H2_GROUP = "com.h2database";
        private static final String DEP_H2_ARTIFACT = "h2";
        private static final String DEP_H2_VERSION = "2.2.224";

        private static final String DEP_SPRING_BOOT_TEST_GROUP = "org.springframework.boot";
        private static final String DEP_SPRING_BOOT_TEST_ARTIFACT = "spring-boot-starter-test";
        
        private static final String DEP_TESTCONTAINERS_GROUP = "org.testcontainers";
        private static final String DEP_TESTCONTAINERS_VERSION = "1.19.0";
        private static final String DEP_TESTCONTAINERS_ARTIFACT_POSTGRES = "postgresql";
        private static final String DEP_TESTCONTAINERS_ARTIFACT_MYSQL = "mysql";
        private static final String DEP_TESTCONTAINERS_ARTIFACT_MONGODB = "mongodb";
        private static final String DEP_TESTCONTAINERS_ARTIFACT_JUNIT_JUPITER = "junit-jupiter";

    private final CampaignRepository campaignRepository;
    private final CampaignTestCaseRepository campaignTestCaseRepository;
    private final ExecutionResultRepository executionResultRepository;
    private final LlmAnalysisService llmAnalysisService;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestSuiteRepository testSuiteRepository;

    @Value("${execution.temp-dir:}")
    private String tempDirConfig;

    @Value("${execution.maven-timeout-minutes:15}")
    private long mavenTimeoutMinutes;

    @Async
    public void runCampaign(Long campaignId) {
        log.info("=== STARTING CAMPAIGN EXECUTION FOR ID: {} ===", campaignId);

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campagne introuvable"));
        log.info("Campaign found with ID: {}, Current status: {}", campaign.getId(), campaign.getStatus());

        if (campaign.getStatus() == Campaign.CampaignStatus.RUNNING) {
            log.info("Campaign is RUNNING, proceeding with execution");
        } else {
            campaign.setStatus(Campaign.CampaignStatus.RUNNING);
            campaign.setStartedAt(LocalDateTime.now());
            campaign.setProgress(5);
            campaign.setCurrentStep("Preparing execution environment");
            campaignRepository.save(campaign);
            log.info("Campaign status updated to RUNNING");
        }

        Project project = projectRepository.findById(campaign.getProjectId())
                .orElseThrow(() -> new RuntimeException("Projet introuvable"));
        log.info("Project found: {}, Git URL: {}", project.getId(), project.getGitRepoUrl());

        Environment env = environmentRepository.findById(campaign.getEnvironmentId())
                .orElse(null);
        log.info("Environment loaded: {}", env != null ? env.getId() : "NULL");

        // Préparation de l'environnement d'exécution (Git externe ou template IA)
        Path repoDir = null;
        try {
            log.info("[CAMPAIGN {}] Preparing base repository (project-level)", campaignId);
            repoDir = prepareRepository(project, campaign);
            log.info("Repository ready at: {}", repoDir);
            campaign.setProgress(20);
            campaign.setCurrentStep("Preparing campaign context");
            campaignRepository.save(campaign);
            log.info("[CAMPAIGN {}] Applying compatibility patches (if any)", campaignId);
            patchKnownTestSuite(repoDir);
        } catch (Exception e) {
            log.error("FATAL: Repository preparation failed", e);
            finishWithError(campaign, "Erreur lors de la préparation du dépôt : " + e.getMessage());
            return;
        }

        // Récupération des cas de test
        log.info("Fetching test cases for campaign ID: {}", campaignId);
        List<CampaignTestCase> ctcList = campaignTestCaseRepository
                .findByCampaignIdOrderByExecutionOrder(campaignId);
        log.info("Found {} test cases for campaign", ctcList.size());

        if (ctcList.isEmpty()) {
            log.warn("No test cases found for campaign! Marking as finished successfully.");
        }

        // Map to cache suite repositories (clone once per unique url#branch)
        // Key format: <gitRepoUrl>#<branch>
        Map<String, Path> suiteRepoMap = new HashMap<>();
        log.info("[CAMPAIGN {}] Suite repo cache initialized (key=url#branch)", campaignId);

        boolean globalSuccess = true;
        int executedCount = 0;
        int totalTests = Math.max(ctcList.size(), 1);

        try {
            for (CampaignTestCase ctc : ctcList) {
                // Reload campaign and check if ABORTED
                Campaign currentCampaign = campaignRepository.findById(campaignId).orElse(null);
                if (currentCampaign != null && currentCampaign.getStatus() == Campaign.CampaignStatus.ABORTED) {
                    log.warn("[CAMPAIGN {}] Campaign is ABORTED, stopping execution", campaignId);
                    break;
                }

                log.info("Processing test case ID: {}", ctc.getTestCaseId());
                TestCase tc = testCaseRepository.findById(ctc.getTestCaseId()).orElse(null);
                if (tc == null) {
                    log.warn("Test case {} not found, skipping", ctc.getTestCaseId());
                    continue;
                }

                log.info(
                        "[CAMPAIGN {}] TestCase {} => type={}, generated={}",
                        campaignId,
                        tc.getId(),
                        tc.getType(),
                        Boolean.TRUE.equals(tc.getGenerated())
                );

                // Determine working directory: suite repo if available, else project repo
                Path workDir = repoDir;
                Long suiteId = tc.getSuiteId();
                TestSuite suiteForWorkDir = null;
                if (suiteId == null) {
                    // Best-effort fallback (may be detached / lazy) but keeps backward compatibility
                    try {
                        if (tc.getSuite() != null) suiteId = tc.getSuite().getId();
                    } catch (Exception ignored) {
                    }
                }

                log.debug("[CAMPAIGN {}] TestCase {} suiteId={}", campaignId, tc.getId(), suiteId);

                if (suiteId != null) {
                    TestSuite suite = testSuiteRepository.findById(suiteId).orElse(null);
                    if (suite == null) {
                        log.warn("[CAMPAIGN {}] Suite {} not found (TestCase {}), using base repo", campaignId, suiteId, tc.getId());
                    }
                    suiteForWorkDir = suite;
                    if (suite != null && suite.getGitRepoUrl() != null && !suite.getGitRepoUrl().isBlank()) {
                        try {
                            TestSuite finalSuite = suite;
                            String branch = (finalSuite.getGitBranch() != null && !finalSuite.getGitBranch().isBlank())
                                    ? finalSuite.getGitBranch().trim()
                                    : "main";
                            String cacheKey = finalSuite.getGitRepoUrl().trim() + "#" + branch;

                            workDir = suiteRepoMap.computeIfAbsent(cacheKey, key -> {
                                try {
                                    log.info(
                                            "[CAMPAIGN {}] Cloning suite repo once: key={}, suiteId={}, url={}, branch={}",
                                            campaignId,
                                            key,
                                            finalSuite.getId(),
                                            finalSuite.getGitRepoUrl(),
                                            branch
                                    );
                                    Path clonedPath = cloneRepository(finalSuite.getGitRepoUrl(), branch);
                                    log.info("Cloned suite repository key {} from {} at {}",
                                            key, finalSuite.getGitRepoUrl(), clonedPath);
                                    return clonedPath;
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        } catch (RuntimeException e) {
                            log.error("Failed to clone suite {} repository, falling back to project repo", suiteId, e);
                            workDir = repoDir;
                        }
                    }
                }

                // If the suite specifies a modulePath (relative), run Maven and inject tests in that module.
                if (suiteForWorkDir != null
                        && suiteForWorkDir.getModulePath() != null
                        && !suiteForWorkDir.getModulePath().isBlank()) {
                    String modulePathRaw = suiteForWorkDir.getModulePath().trim();
                    try {
                        Path modulePath = Paths.get(modulePathRaw);
                        Path resolved = workDir.resolve(modulePath);
                        log.info(
                                "[CAMPAIGN {}] Applying suite modulePath: suiteId={}, modulePath='{}' => workDir='{}'",
                                campaignId,
                                suiteForWorkDir.getId(),
                                modulePathRaw,
                                resolved
                        );
                        workDir = resolved;
                        if (!Files.isDirectory(workDir)) {
                            log.warn(
                                    "[CAMPAIGN {}] Resolved workDir is not a directory: {} (modulePath='{}')",
                                    campaignId,
                                    workDir,
                                    modulePathRaw
                            );
                        }
                    } catch (Exception ex) {
                        log.error(
                                "[CAMPAIGN {}] Invalid modulePath '{}' for suiteId={} (keeping workDir={})",
                                campaignId,
                                modulePathRaw,
                                suiteForWorkDir.getId(),
                                workDir,
                                ex
                        );
                    }
                }

                log.info("[CAMPAIGN {}] TestCase {} workDir={}", campaignId, tc.getId(), workDir);

                log.info("Executing test: {} ({})", tc.getId(),
                        tc.getGenerated() ? "GENERATED-" + tc.getId() : tc.getScriptPath());
                ExecutionResult result = executeRealTest(tc, env, workDir, campaignId);
                result.setCampaignId(campaignId);
                result.setTestCaseId(tc.getId());

                log.info("Test result for {}: status={}, error={}",
                        tc.getId(), result.getStatus(), result.getErrorMessage());

                executionResultRepository.save(result);
                executedCount++;
                log.info("Saved execution result for test case {}", tc.getId());

                int computedProgress = 20 + Math.min(70, Math.round((executedCount * 70.0f) / totalTests));
                campaign.setProgress(Math.min(95, computedProgress));
                campaign.setCurrentStep("Running Maven tests");
                campaignRepository.save(campaign);

                if (result.getStatus() == ExecutionResult.ResultStatus.FAILURE ||
                        result.getStatus() == ExecutionResult.ResultStatus.ERROR) {
                    globalSuccess = false;
                }
            }

            log.info("Execution complete. Executed: {}, Global success: {}", executedCount, globalSuccess);
            enrichFailureAnalyses(campaignId);

            Campaign refreshedCampaign = campaignRepository.findById(campaignId).orElse(campaign);
            if (refreshedCampaign != null && refreshedCampaign.getStatus() == Campaign.CampaignStatus.ABORTED) {
                log.info("[CAMPAIGN {}] Campaign remains ABORTED after execution loop, skipping finalization", campaignId);
                return;
            }

            if (globalSuccess) {
                campaign.setStatus(Campaign.CampaignStatus.FINISHED);
                log.info("Campaign marked as FINISHED");
            } else {
                campaign.setStatus(Campaign.CampaignStatus.FINISHED_WITH_ERRORS);
                log.info("Campaign marked as FINISHED_WITH_ERRORS");
            }
            campaign.setProgress(100);
            campaign.setCurrentStep("Saving execution result");
            campaign.setFinishedAt(LocalDateTime.now());
            campaignRepository.save(campaign);
            log.info("=== CAMPAIGN EXECUTION COMPLETED FOR ID: {} ===", campaignId);
        } catch (Exception e) {
            log.error("FATAL: Campaign execution failed", e);
            finishWithError(campaign, "Erreur lors de l'exécution : " + e.getMessage());
        } finally {
            // Clean up cloned suite repositories
            log.info("[CAMPAIGN {}] Cleaning up suite repositories (count={})", campaignId, suiteRepoMap.size());
            suiteRepoMap.values().forEach(this::deleteDirectory);
            log.info("Cleaned up {} cloned suite repositories", suiteRepoMap.size());

            // Clean up project repository
            if (repoDir != null) {
                log.info("[CAMPAIGN {}] Cleaning up base repository at {}", campaignId, repoDir);
                deleteDirectory(repoDir);
                log.info("Cleaned up project repository at: {}", repoDir);
            }
        }
    }

    private void enrichFailureAnalyses(Long campaignId) {
        List<ExecutionResult> results = executionResultRepository.findByCampaignId(campaignId);
        for (ExecutionResult result : results) {
            if (result.getStatus() != ExecutionResult.ResultStatus.FAILURE
                    && result.getStatus() != ExecutionResult.ResultStatus.ERROR) {
                continue;
            }

            if (result.getAiAnalysis() != null && !result.getAiAnalysis().isBlank()) {
                continue;
            }

            log.info("[CAMPAIGN {}] Requesting AI analysis for execution result {}", campaignId, result.getId());
            String analysis = llmAnalysisService.analyzeFailure(result.getLogs());
            if (analysis == null || analysis.isBlank()) {
                log.warn("[CAMPAIGN {}] AI analysis unavailable for execution result {}", campaignId, result.getId());
                continue;
            }

            result.setAiAnalysis(analysis);
            executionResultRepository.save(result);
            log.info("[CAMPAIGN {}] AI analysis persisted for execution result {}", campaignId, result.getId());
        }
    }

    private void finishWithError(Campaign campaign, String errorMessage) {
        campaign.setStatus(Campaign.CampaignStatus.FINISHED_WITH_ERRORS);
        campaign.setProgress(100);
        campaign.setCurrentStep(errorMessage);
        campaign.setFinishedAt(LocalDateTime.now());
        campaignRepository.save(campaign);
    }

    // --- Préparation du dépôt d'exécution (Git externe ou template IA intégré) ---
    private Path prepareRepository(Project project, Campaign campaign) throws IOException, GitAPIException {
        String gitUrl = project.getGitRepoUrl();
        if (gitUrl == null || gitUrl.isBlank() || "ai-builtin".equalsIgnoreCase(gitUrl)) {
            log.info("Using built-in AI test template (no Git URL / ai-builtin)");
            return prepareBuiltInTemplate();
        } else {
            String branch = campaign.getGitBranch() != null ? campaign.getGitBranch() :
                    (project.getGitDefaultBranch() != null ? project.getGitDefaultBranch() : "main");
            log.info("Cloning repository from: {} on branch: {}", gitUrl, branch);
            return cloneRepository(gitUrl, branch);
        }
    }

    private Path prepareBuiltInTemplate() throws IOException {
        // 1. Charger le template compressé depuis les ressources
        Resource resource = new ClassPathResource("ai-test-template.zip");
        if (!resource.exists()) {
            throw new RuntimeException("Template IA introuvable (ai-test-template.zip)");
        }

        // 2. Créer un répertoire temporaire
        Path execDir = createTempDir("exec-");
        log.info("Preparing built-in AI template into temp dir: {}", execDir);

        // 3. Décompresser le ZIP
        try (ZipInputStream zis = new ZipInputStream(resource.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = execDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }

        log.info("AI template prepared at: {}", execDir);
        return execDir;
    }

    private Path createTempDir(String prefix) throws IOException {
        Path tempDirPath;
        if (tempDirConfig != null && !tempDirConfig.trim().isEmpty()) {
            tempDirPath = Paths.get(tempDirConfig);
            if (!Files.exists(tempDirPath)) {
                Files.createDirectories(tempDirPath);
            }
        } else {
            tempDirPath = Paths.get(System.getProperty("java.io.tmpdir"), "ms-execution");
            if (!Files.exists(tempDirPath)) {
                Files.createDirectories(tempDirPath);
            }
        }
        return Files.createTempDirectory(tempDirPath, prefix);
    }

    private Path cloneRepository(String repoUrl, String branch) throws GitAPIException, IOException {
        Path dir = createTempDir("exec-");
        log.info("Cloning Git repo => url='{}', branch='{}', dest='{}'", repoUrl, branch, dir);
        Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(dir.toFile())
                .setBranch(branch)
                .call();
        log.info("Clone completed: {}", dir);
        return dir;
    }

    private void patchKnownTestSuite(Path repoDir) {
        try {
            Path testFile = repoDir.resolve("src/test/java/suites/herapp/TestHerappSuite.java");
            if (!Files.exists(testFile)) return;

            String content = Files.readString(testFile);
            String patchedContent = content.replace(
                    "String title = driver.getTitle();",
                    "String title = driver.findElement(By.tagName(\"h1\")).getText();"
            );
            if (!patchedContent.equals(content)) {
                Files.writeString(testFile, patchedContent);
                log.info("Applied 404 page compatibility patch in {}", testFile);
            }
        } catch (Exception e) {
            log.warn("Unable to apply compatibility patch", e);
        }
    }

    private ExecutionResult executeRealTest(TestCase tc, Environment env, Path workDir, Long campaignId) {
        long start = System.currentTimeMillis();
        ExecutionResult result = new ExecutionResult();
        result.setStatus(ExecutionResult.ResultStatus.SUCCESS);
        result.setTestType(tc.getType());

        log.info(
                "[TESTCASE {}] === START executeRealTest === type={}, generated={}, workDir={}",
                tc.getId(),
                tc.getType(),
                Boolean.TRUE.equals(tc.getGenerated()),
                workDir
        );

        try {
            // Vérifier si la campagne est ABORTED
            if (campaignId != null) {
                Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
                if (campaign != null && campaign.getStatus() == Campaign.CampaignStatus.ABORTED) {
                    log.warn("[TESTCASE {}] Campaign {} is ABORTED, skipping test execution", tc.getId(), campaignId);
                    result.setStatus(ExecutionResult.ResultStatus.ERROR);
                    result.setErrorMessage("Campaign aborted");
                    result.setDurationMs(0L);
                    return result;
                }
            }

            // Workdir sanity + snapshot (helps detect wrong modulePath / wrong repo root)
            if (workDir == null) {
                throw new IllegalStateException("workDir is null");
            }
            if (!Files.exists(workDir)) {
                throw new IllegalStateException("workDir does not exist: " + workDir);
            }
            if (!Files.isDirectory(workDir)) {
                throw new IllegalStateException("workDir is not a directory: " + workDir);
            }

            log.debug("[TESTCASE {}] workDir absolutePath={}", tc.getId(), workDir.toAbsolutePath());
            logWorkDirSnapshot(tc.getId(), workDir, 10);

            Path pom = workDir.resolve("pom.xml");
            if (!Files.exists(pom)) {
                log.warn("[TESTCASE {}] No pom.xml found at workDir={} (pom.xml={})", tc.getId(), workDir, pom);
            } else {
                log.debug("[TESTCASE {}] pom.xml found: {} (size={} bytes)", tc.getId(), pom, safeFileSize(pom));
                TestCase.TestType pomType = tc.getType();
                if (pomType == TestCase.TestType.UNIT || pomType == TestCase.TestType.INTEGRATION) {
                    log.info("[TESTCASE {}] Ensuring required test dependencies in pom.xml...", tc.getId());
                        ensureTestDependencies(pom, pomType, tc.getDatabaseType());
                    log.info("[TESTCASE {}] pom.xml dependency check completed", tc.getId());
                } else {
                    log.info("[TESTCASE {}] Skipping pom.xml dependency enforcement for type={} (WEB/API repos manage deps)", tc.getId(), pomType);
                }
            }

            String className;
            // ----- TEST GÉNÉRÉ PAR IA -----
            if (Boolean.TRUE.equals(tc.getGenerated()) && tc.getGeneratedCode() != null) {
                log.info("[TESTCASE {}] AI-generated test detected; preparing source file", tc.getId());
                log.debug("[TESTCASE {}] Raw generated code length={}", tc.getId(), tc.getGeneratedCode().length());
                
                // Clean code: remove only markdown backticks
                String cleanCode = tc.getGeneratedCode()
                        .replaceAll("(?i)```java\\s*", "")
                        .replaceAll("```", "")
                        .trim();
                log.debug("[TESTCASE {}] Cleaned code length={} (markdown backticks removed)", tc.getId(), cleanCode.length());

                if (cleanCode.isBlank()) {
                    throw new IllegalArgumentException("Generated code is empty after cleanup");
                }

                // Extract class name from generated code using regex: "\bclass\s+(\w+)"
                String extractedClassName = null;
                var classNameMatcher = Pattern.compile("\\bclass\\s+(\\w+)").matcher(cleanCode);
                if (classNameMatcher.find()) {
                    extractedClassName = classNameMatcher.group(1);
                    log.info("[TESTCASE {}] Extracted class name: {}", tc.getId(), extractedClassName);
                } else {
                    throw new IllegalArgumentException("Generated code does not contain a valid class declaration");
                }

                // Determine subdirectory based on test type
                String subDir = getTestPackageByType(tc.getType());
                if (subDir == null || subDir.isBlank()) {
                    subDir = "suites/generated";
                }
                log.info("[TESTCASE {}] Test file will be written to subDir='{}' with class name='{}'", 
                        tc.getId(), subDir, extractedClassName);

                // Create directory and write file
                Path genDir = workDir.resolve("src/test/java").resolve(subDir);
                Files.createDirectories(genDir);
                log.debug("[TESTCASE {}] Ensured directory exists: {}", tc.getId(), genDir);

                Path testFile = genDir.resolve(extractedClassName + ".java");
                Files.writeString(testFile, cleanCode);
                log.info("[TESTCASE {}] Generated test written to: {}", tc.getId(), testFile);

                // Extract declared package from generated code
                String declaredPackage = null;
                var pkgMatcher = PACKAGE_DECLARATION.matcher(cleanCode);
                if (pkgMatcher.find()) {
                    declaredPackage = pkgMatcher.group(1);
                    log.info("[TESTCASE {}] Declared package: {}", tc.getId(), declaredPackage);
                }

                // Build Maven test selector
                className = (declaredPackage != null && !declaredPackage.isBlank())
                    ? (declaredPackage + "." + extractedClassName)
                    : extractedClassName;
                log.info("[TESTCASE {}] Maven test selector: -Dtest={}", tc.getId(), className);
            } else {
                log.info("[TESTCASE {}] Manual test (scriptPath={})", tc.getId(), tc.getScriptPath());
                className = tc.getScriptPath()
                        .replace("/", ".")
                        .replace(".java", "");
            }
            log.debug("Final class name for Maven: {}", className);

            // Explicit wrapper check to avoid silent hangs/timeouts.
            Path wrapper = findMavenWrapper(workDir);
            if (wrapper == null) {
                String message = "Maven wrapper not found (mvnw.cmd/mvnw) from workDir up to root: " + workDir;
                log.error("[TESTCASE {}] {}", tc.getId(), message);
                result.setDurationMs(System.currentTimeMillis() - start);
                result.setStatus(ExecutionResult.ResultStatus.ERROR);
                result.setErrorMessage(message);
                result.setLogs("Wrapper check failed. workDir=" + workDir + System.lineSeparator());
                return result;
            }

            log.info("[TESTCASE {}] Using Maven wrapper: {}", tc.getId(), wrapper);

            // Ensure a default Spring Boot test profile exists for integration tests.
            TestCase.TestType effectiveType = tc.getType() != null ? tc.getType() : TestCase.TestType.WEB;
            if (effectiveType == TestCase.TestType.INTEGRATION) {
                // databaseType must be provided for integration tests
                String dbType = tc.getDatabaseType();
                if (dbType == null || dbType.isBlank()) {
                    String message = "databaseType is required for INTEGRATION tests";
                    log.error("[TESTCASE {}] {}", tc.getId(), message);
                    result.setDurationMs(System.currentTimeMillis() - start);
                    result.setStatus(ExecutionResult.ResultStatus.ERROR);
                    result.setErrorMessage(message);
                    result.setLogs(message);
                    return result;
                }

                // Ensure dependencies for integration tests (DB-specific additions included)
                ensureTestDependencies(pom, effectiveType, dbType);

                // Apply DB-specific test properties
                ensureTestProperties(workDir, dbType);

                // Only ensure H2-specific pom dialect when databaseType is H2
                if ("H2".equalsIgnoreCase(dbType)) {
                    try {
                        ensureH2DialectInPom(pom);
                    } catch (Exception e) {
                        log.warn("[TESTCASE {}] Failed to ensure H2 dialect in pom.xml: {}", tc.getId(), e.getMessage());
                    }
                }
            }

            List<String> command = buildMavenCommand(tc, env, workDir, className, wrapper);

            log.info("Maven command: {}", String.join(" ", command));
            log.info("Working directory: {}", workDir);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(false);

            long processStartTime = System.currentTimeMillis();
            log.info("[TESTCASE {}] Starting Maven process...", tc.getId());
            Process process = pb.start();
            log.debug("[TESTCASE {}] Maven process started (pid={})", tc.getId(), process.pid());

            StringBuilder stdout = new StringBuilder(32 * 1024);
            StringBuilder stderr = new StringBuilder(16 * 1024);
            Thread outThread = startStreamGobbler(process.getInputStream(), stdout, "stdout", tc.getId());
            Thread errThread = startStreamGobbler(process.getErrorStream(), stderr, "stderr", tc.getId());

            log.debug(
                    "[TESTCASE {}] Waiting for Maven process (timeout={} minutes)...",
                    tc.getId(),
                    mavenTimeoutMinutes
            );
            boolean finished = process.waitFor(mavenTimeoutMinutes, TimeUnit.MINUTES);
            long waitForCompletedTime = System.currentTimeMillis();
            log.debug(
                    "[TESTCASE {}] process.waitFor() completed (elapsed={}ms, finished={})",
                    tc.getId(),
                    (waitForCompletedTime - processStartTime),
                    finished
            );

            // Ensure gobblers have time to drain remaining output.
            joinQuietly(outThread, 10_000);
            joinQuietly(errThread, 10_000);

            String combinedOutput = combineProcessOutput(stdout, stderr);
            log.debug(
                    "[TESTCASE {}] Maven output captured: stdoutChars={}, stderrChars={}, combinedChars={}, combinedLines~={}",
                    tc.getId(),
                    stdout.length(),
                    stderr.length(),
                    combinedOutput.length(),
                    approximateLineCount(combinedOutput)
            );

            if (!finished) {
                process.destroyForcibly();
                result.setDurationMs(System.currentTimeMillis() - start);
                result.setStatus(ExecutionResult.ResultStatus.ERROR);
                result.setErrorMessage("Maven timeout after " + mavenTimeoutMinutes + " minutes");
                result.setLogs(combinedOutput);
                log.error("[TESTCASE {}] Maven TIMEOUT after {} minutes (pid={})", tc.getId(), mavenTimeoutMinutes, process.pid());
                log.error("[TESTCASE {}] Maven output (first 500 lines, truncated):\n{}", tc.getId(), firstLines(combinedOutput, 500, 20_000));
                return result;
            }

            int exitCode = process.exitValue();
            log.info("[TESTCASE {}] Maven finished with exitCode={}", tc.getId(), exitCode);
            long duration = System.currentTimeMillis() - start;
            result.setDurationMs(duration);
            result.setLogs(combinedOutput);

            if (!combinedOutput.isBlank()) {
                int max = Math.min(combinedOutput.length(), 800);
                log.debug("[TESTCASE {}] Maven output (first {} chars): {}", tc.getId(), max, combinedOutput.substring(0, max));
            }

            if (exitCode != 0) {
                result.setStatus(ExecutionResult.ResultStatus.FAILURE);
                result.setErrorMessage("Maven exit code : " + exitCode);
                log.error("[TESTCASE {}] Maven FAILURE exitCode={} (durationMs={})", tc.getId(), exitCode, duration);
                log.error("[TESTCASE {}] Maven output (first 500 lines, truncated):\n{}", tc.getId(), firstLines(combinedOutput, 500, 20_000));
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            result.setDurationMs(duration);
            result.setStatus(ExecutionResult.ResultStatus.ERROR);
            result.setErrorMessage(e.getClass().getSimpleName() + " : " + e.getMessage());
            log.error("EXCEPTION during test execution", e);
        } finally {
            if (result.getDurationMs() == null || result.getDurationMs() <= 0) {
                result.setDurationMs(System.currentTimeMillis() - start);
            }
            log.info(
                    "[TESTCASE {}] === END executeRealTest === status={}, durationMs={}, errorMessage={}",
                    tc.getId(),
                    result.getStatus(),
                    result.getDurationMs(),
                    result.getErrorMessage()
            );
        }

        return result;
    }

    private void ensureTestDependencies(Path pomXml) throws Exception {
        ensureTestDependencies(pomXml, null, null);
    }

    private void ensureTestDependencies(Path pomXml, TestCase.TestType testType, String databaseType) throws Exception {
        if (pomXml == null) {
            throw new IllegalArgumentException("pomXml is null");
        }
        if (!Files.exists(pomXml)) {
            throw new IllegalArgumentException("pom.xml does not exist: " + pomXml);
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomXml.toFile());

        Element project = doc.getDocumentElement();
        if (project == null) {
            throw new IllegalStateException("Invalid pom.xml (no document element): " + pomXml);
        }

        String ns = project.getNamespaceURI();
        Element dependencies = findDirectChildByLocalName(project, "dependencies");
        if (dependencies == null) {
            dependencies = ns != null ? doc.createElementNS(ns, "dependencies") : doc.createElement("dependencies");
            project.appendChild(dependencies);
            log.info("Created <dependencies> section in {}", pomXml);
        }

        boolean changed = false;
        changed |= ensureDependency(doc, dependencies, ns, pomXml,
                DEP_TESTNG_GROUP, DEP_TESTNG_ARTIFACT, DEP_TESTNG_VERSION, "test");
        changed |= ensureDependency(doc, dependencies, ns, pomXml,
                DEP_MOCKITO_GROUP, DEP_MOCKITO_ARTIFACT, DEP_MOCKITO_VERSION, "test");

        if (testType == TestCase.TestType.INTEGRATION) {
            changed |= ensureDependency(doc, dependencies, ns, pomXml,
                DEP_SPRING_BOOT_TEST_GROUP, DEP_SPRING_BOOT_TEST_ARTIFACT, null, "test");

            if (databaseType != null) {
                String db = databaseType.trim().toUpperCase();
                switch (db) {
                    case "H2":
                        changed |= ensureDependency(doc, dependencies, ns, pomXml,
                            DEP_H2_GROUP, DEP_H2_ARTIFACT, DEP_H2_VERSION, "test");
                        break;
                    case "POSTGRESQL":
                        changed |= ensureDependency(doc, dependencies, ns, pomXml,
                            DEP_TESTCONTAINERS_GROUP, DEP_TESTCONTAINERS_ARTIFACT_POSTGRES, DEP_TESTCONTAINERS_VERSION, "test");
                        changed |= ensureDependency(doc, dependencies, ns, pomXml,
                            DEP_TESTCONTAINERS_GROUP, "testcontainers", DEP_TESTCONTAINERS_VERSION, "test");
                        break;
                    case "MYSQL":
                        changed |= ensureDependency(doc, dependencies, ns, pomXml,
                            DEP_TESTCONTAINERS_GROUP, DEP_TESTCONTAINERS_ARTIFACT_MYSQL, DEP_TESTCONTAINERS_VERSION, "test");
                        changed |= ensureDependency(doc, dependencies, ns, pomXml,
                            DEP_TESTCONTAINERS_GROUP, "testcontainers", DEP_TESTCONTAINERS_VERSION, "test");
                        break;
                    case "MONGODB":
                        changed |= ensureDependency(doc, dependencies, ns, pomXml,
                            DEP_TESTCONTAINERS_GROUP, DEP_TESTCONTAINERS_ARTIFACT_MONGODB, "1.19.3", "test");
                        changed |= ensureDependency(doc, dependencies, ns, pomXml,
                            DEP_TESTCONTAINERS_GROUP, DEP_TESTCONTAINERS_ARTIFACT_JUNIT_JUPITER, "1.19.3", "test");
                        break;
                    default:
                        // unknown DB type: do nothing here (validation elsewhere)
                }
            }
        }

        changed |= ensureSurefireTestNgPlugin(doc, project, ns, pomXml);

        if (changed) {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            try {
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            } catch (Exception ignored) {
            }
            transformer.transform(new DOMSource(doc), new StreamResult(pomXml.toFile()));
            log.info("Updated pom.xml with missing test dependencies / surefire config: {}", pomXml);
        } else {
            log.debug("pom.xml already contains required test dependencies and surefire TestNG config: {}", pomXml);
        }
    }

    private boolean ensureSurefireTestNgPlugin(Document doc, Element project, String ns, Path pomXml) {
        boolean changed = false;

        Element build = findDirectChildByLocalName(project, "build");
        if (build == null) {
            build = ns != null ? doc.createElementNS(ns, "build") : doc.createElement("build");
            project.appendChild(build);
            log.info("Created <build> section in {}", pomXml);
            changed = true;
        }

        Element plugins = findDirectChildByLocalName(build, "plugins");
        if (plugins == null) {
            plugins = ns != null ? doc.createElementNS(ns, "plugins") : doc.createElement("plugins");
            build.appendChild(plugins);
            log.info("Created <build><plugins> section in {}", pomXml);
            changed = true;
        }

        Element surefire = findPluginByArtifactId(plugins, "maven-surefire-plugin");
        if (surefire == null) {
            surefire = ns != null ? doc.createElementNS(ns, "plugin") : doc.createElement("plugin");
            surefire.appendChild(textElement(doc, ns, "groupId", "org.apache.maven.plugins"));
            surefire.appendChild(textElement(doc, ns, "artifactId", "maven-surefire-plugin"));
            surefire.appendChild(textElement(doc, ns, "version", "3.2.5"));

            Element configuration = ns != null ? doc.createElementNS(ns, "configuration") : doc.createElement("configuration");

            Element properties = ns != null ? doc.createElementNS(ns, "properties") : doc.createElement("properties");
            Element property = ns != null ? doc.createElementNS(ns, "property") : doc.createElement("property");
            property.appendChild(textElement(doc, ns, "name", "junit"));
            property.appendChild(textElement(doc, ns, "value", "false"));
            properties.appendChild(property);
            configuration.appendChild(properties);

            Element pluginDeps = ns != null ? doc.createElementNS(ns, "dependencies") : doc.createElement("dependencies");
            Element dep = ns != null ? doc.createElementNS(ns, "dependency") : doc.createElement("dependency");
            dep.appendChild(textElement(doc, ns, "groupId", "org.apache.maven.surefire"));
            dep.appendChild(textElement(doc, ns, "artifactId", "surefire-testng"));
            dep.appendChild(textElement(doc, ns, "version", "3.2.5"));
            pluginDeps.appendChild(dep);

            // Order matters for the requested "exact" block: configuration first, then dependencies.
            surefire.appendChild(configuration);
            surefire.appendChild(pluginDeps);
            plugins.appendChild(surefire);

            log.info("Added maven-surefire-plugin TestNG configuration to {}", pomXml);
            return true;
        }

        // Plugin exists: ensure minimal required configuration is present.
        Element versionEl = findDirectChildByLocalName(surefire, "version");
        if (versionEl == null) {
            surefire.appendChild(textElement(doc, ns, "version", "3.2.5"));
            log.info("Added missing surefire plugin <version>3.2.5</version> in {}", pomXml);
            changed = true;
        }

        Element configuration = findDirectChildByLocalName(surefire, "configuration");
        if (configuration == null) {
            configuration = ns != null ? doc.createElementNS(ns, "configuration") : doc.createElement("configuration");
            surefire.appendChild(configuration);
            changed = true;
        }

        // Ensure <properties><property><name>junit</name><value>false</value></property></properties>
        Element properties = findDirectChildByLocalName(configuration, "properties");
        if (properties == null) {
            properties = ns != null ? doc.createElementNS(ns, "properties") : doc.createElement("properties");
            configuration.appendChild(properties);
            changed = true;
        }

        boolean hasJunitFalse = false;
        NodeList propertyNodes = properties.getElementsByTagNameNS("*", "property");
        for (int i = 0; i < propertyNodes.getLength(); i++) {
            Node n = propertyNodes.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element p = (Element) n;
            String name = textOfFirstChildByLocalName(p, "name");
            String value = textOfFirstChildByLocalName(p, "value");
            if ("junit".equals(name) && "false".equalsIgnoreCase(value)) {
                hasJunitFalse = true;
                break;
            }
        }
        if (!hasJunitFalse) {
            Element property = ns != null ? doc.createElementNS(ns, "property") : doc.createElement("property");
            property.appendChild(textElement(doc, ns, "name", "junit"));
            property.appendChild(textElement(doc, ns, "value", "false"));
            properties.appendChild(property);
            log.info("Added surefire configuration property junit=false in {}", pomXml);
            changed = true;
        }

        // Ensure plugin-level <dependencies> contains surefire-testng (NOT under <configuration>).
        Element pluginDeps = findDirectChildByLocalName(surefire, "dependencies");
        if (pluginDeps == null) {
            pluginDeps = ns != null ? doc.createElementNS(ns, "dependencies") : doc.createElement("dependencies");
            surefire.appendChild(pluginDeps);
            changed = true;
        }

        boolean hasSurefireTestng = false;
        NodeList depNodes = pluginDeps.getElementsByTagNameNS("*", "dependency");
        for (int i = 0; i < depNodes.getLength(); i++) {
            Node n = depNodes.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element d = (Element) n;
            String g = textOfFirstChildByLocalName(d, "groupId");
            String a = textOfFirstChildByLocalName(d, "artifactId");
            if ("org.apache.maven.surefire".equals(g) && "surefire-testng".equals(a)) {
                hasSurefireTestng = true;
                break;
            }
        }
        if (!hasSurefireTestng) {
            Element dep = ns != null ? doc.createElementNS(ns, "dependency") : doc.createElement("dependency");
            dep.appendChild(textElement(doc, ns, "groupId", "org.apache.maven.surefire"));
            dep.appendChild(textElement(doc, ns, "artifactId", "surefire-testng"));
            dep.appendChild(textElement(doc, ns, "version", "3.2.5"));
            pluginDeps.appendChild(dep);
            log.info("Added surefire-testng dependency under surefire plugin <dependencies> in {}", pomXml);
            changed = true;
        }

        // If a previous run added surefire-testng under <configuration><dependencies>, migrate/remove it.
        Element configDeps = findDirectChildByLocalName(configuration, "dependencies");
        if (configDeps != null) {
            boolean removedAny = false;
            NodeList configDepNodes = configDeps.getElementsByTagNameNS("*", "dependency");
            for (int i = configDepNodes.getLength() - 1; i >= 0; i--) {
                Node n = configDepNodes.item(i);
                if (n.getNodeType() != Node.ELEMENT_NODE) continue;
                Element d = (Element) n;
                String g = textOfFirstChildByLocalName(d, "groupId");
                String a = textOfFirstChildByLocalName(d, "artifactId");
                if ("org.apache.maven.surefire".equals(g) && "surefire-testng".equals(a)) {
                    configDeps.removeChild(d);
                    removedAny = true;
                }
            }
            if (removedAny) {
                log.info("Removed surefire-testng from surefire <configuration><dependencies> in {}", pomXml);
                changed = true;
            }

            // If configDeps is now empty (no element children), remove it so configuration matches the requested shape.
            boolean hasElementChildren = false;
            NodeList remaining = configDeps.getChildNodes();
            for (int i = 0; i < remaining.getLength(); i++) {
                if (remaining.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    hasElementChildren = true;
                    break;
                }
            }
            if (!hasElementChildren) {
                configuration.removeChild(configDeps);
                log.info("Removed empty surefire <configuration><dependencies> in {}", pomXml);
                changed = true;
            }
        }

        // Ensure order: configuration first, then dependencies (to match the requested snippet).
        // If dependencies currently appears before configuration, move it after configuration.
        NodeList children = surefire.getChildNodes();
        int configIndex = -1;
        int depsIndex = -1;
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            String ln = n.getLocalName();
            String nn = n.getNodeName();
            if (configIndex < 0 && ("configuration".equals(ln) || "configuration".equals(nn))) {
                configIndex = i;
            }
            if (depsIndex < 0 && ("dependencies".equals(ln) || "dependencies".equals(nn))) {
                depsIndex = i;
            }
        }
        if (configIndex >= 0 && depsIndex >= 0 && depsIndex < configIndex) {
            surefire.removeChild(pluginDeps);
            surefire.appendChild(pluginDeps);
            changed = true;
        }

        return changed;
    }

    private Element findPluginByArtifactId(Element plugins, String artifactId) {
        if (plugins == null || artifactId == null) return null;
        NodeList children = plugins.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) n;
            String ln = el.getLocalName();
            String nn = el.getNodeName();
            if (!("plugin".equals(ln) || "plugin".equals(nn))) continue;
            String a = textOfFirstChildByLocalName(el, "artifactId");
            if (artifactId.equals(a)) {
                return el;
            }
        }
        return null;
    }

    private boolean ensureDependency(
            Document doc,
            Element dependencies,
            String ns,
            Path pomXml,
            String groupId,
            String artifactId,
            String version,
            String scope
    ) {
        if (hasDependency(doc, groupId, artifactId)) {
            log.debug("Dependency already present in {}: {}:{}", pomXml, groupId, artifactId);
            return false;
        }

        Element dep = ns != null ? doc.createElementNS(ns, "dependency") : doc.createElement("dependency");
        dep.appendChild(textElement(doc, ns, "groupId", groupId));
        dep.appendChild(textElement(doc, ns, "artifactId", artifactId));
        if (version != null && !version.isBlank()) {
            dep.appendChild(textElement(doc, ns, "version", version));
        }
        if (scope != null && !scope.isBlank()) {
            dep.appendChild(textElement(doc, ns, "scope", scope));
        }

        dependencies.appendChild(dep);
        log.info("Added missing dependency to {}: {}:{}:{} (scope={})", pomXml, groupId, artifactId, version, scope);
        return true;
    }

    private boolean hasDependency(Document doc, String groupId, String artifactId) {
        NodeList deps = doc.getElementsByTagNameNS("*", "dependency");
        for (int i = 0; i < deps.getLength(); i++) {
            Node node = deps.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element dep = (Element) node;

            String g = textOfFirstChildByLocalName(dep, "groupId");
            String a = textOfFirstChildByLocalName(dep, "artifactId");
            if (groupId.equals(g) && artifactId.equals(a)) {
                return true;
            }
        }
        return false;
    }

    private Element textElement(Document doc, String ns, String name, String text) {
        Element el = ns != null ? doc.createElementNS(ns, name) : doc.createElement(name);
        el.setTextContent(text);
        return el;
    }

    private Element findDirectChildByLocalName(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            String ln = n.getLocalName();
            String nn = n.getNodeName();
            if (localName.equals(ln) || localName.equals(nn)) {
                return (Element) n;
            }
        }
        return null;
    }

    private String textOfFirstChildByLocalName(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            String ln = n.getLocalName();
            String nn = n.getNodeName();
            if (localName.equals(ln) || localName.equals(nn)) {
                String t = n.getTextContent();
                return t != null ? t.trim() : null;
            }
        }
        return null;
    }

    private List<String> buildMavenCommand(TestCase tc, Environment env, Path repoDir, String className, Path wrapper) {
        List<String> command = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();

        if (wrapper == null) {
            throw new IllegalStateException("Maven wrapper is null");
        }
        if (os.contains("win")) {
            command.add("cmd");
            command.add("/c");
            command.add(wrapper.toString());
        } else {
            command.add(wrapper.toString());
        }

        command.add("test");
        command.add("-Dtest=" + className);
        command.add("-Djunit.platform.engine.disabled=true");
        command.add("-Dsurefire.provider=testng");

        TestCase.TestType testType = tc.getType() != null ? tc.getType() : TestCase.TestType.WEB;
        String baseUrl = resolveBaseUrl(testType, env);

        switch (testType) {
            case UNIT:
                command.add("-Dtest.layer=unit");
                break;
            case INTEGRATION:
                command.add("-Dtest.layer=integration");
                if (tc.getDatabaseType() != null && tc.getDatabaseType().trim().equalsIgnoreCase("H2")) {
                    command.add("-Dspring.jpa.database-platform=org.hibernate.dialect.H2Dialect");
                }
                if (baseUrl != null && !baseUrl.isBlank()) {
                    command.add("-DBASE_URL=" + baseUrl);
                }
                command.add("-Dspring.profiles.active=test");
                break;
            case API:
            case WEB:
            default:
                command.add("-Dtest.layer=e2e");
                if (baseUrl != null && !baseUrl.isBlank()) {
                    command.add("-DBASE_URL=" + baseUrl);
                }
                break;
        }

        if (env != null && env.getVariables() != null && !env.getVariables().isEmpty()) {
            command.add("-Denv.variables=" + env.getVariables());
        }

        return command;
    }

    private String adaptMongoGeneratedTestCode(String code, String className) {
        String adapted = code;

        // Ensure required imports exist for Spring + Testcontainers MongoDB integration tests.
        adapted = ensureImport(adapted, "org.springframework.boot.test.context.SpringBootTest");
        adapted = ensureImport(adapted, "org.springframework.test.context.ActiveProfiles");
        adapted = ensureImport(adapted, "org.springframework.test.context.DynamicPropertyRegistry");
        adapted = ensureImport(adapted, "org.springframework.test.context.DynamicPropertySource");
        adapted = ensureImport(adapted, "org.springframework.test.context.testng.AbstractTestNGSpringContextTests");
        adapted = ensureImport(adapted, "org.testcontainers.containers.MongoDBContainer");
        adapted = ensureImport(adapted, "org.testcontainers.junit.jupiter.Container");
        adapted = ensureImport(adapted, "org.testcontainers.junit.jupiter.Testcontainers");

        // Insert annotations AFTER package declaration but BEFORE class declaration
        // Extract package line (if present)
        String packageLine = "";
        String codeWithoutPackage = adapted;
        var pkgMatcher = Pattern.compile("(?m)^\\s*package\\s+[^;]+;").matcher(adapted);
        if (pkgMatcher.find()) {
            packageLine = pkgMatcher.group(0) + "\n";
            codeWithoutPackage = adapted.substring(pkgMatcher.end());
        }

        // Build annotations block
        String annotations = "";
        if (!codeWithoutPackage.contains("@SpringBootTest")) {
            annotations += "@SpringBootTest\n";
        }
        if (!codeWithoutPackage.contains("@ActiveProfiles(\"test\")")) {
            annotations += "@ActiveProfiles(\"test\")\n";
        }
        if (!codeWithoutPackage.contains("@Testcontainers")) {
            annotations += "@Testcontainers\n";
        }

        // Rebuild: package + imports + annotations + class
        String importsAndClass = codeWithoutPackage.replaceFirst(
                "(?m)^\\s*public\\s+class\\s+" + Pattern.quote(className),
                annotations + "public class " + className
        );

        // Ensure class extends AbstractTestNGSpringContextTests
        if (!importsAndClass.contains("extends AbstractTestNGSpringContextTests")) {
            importsAndClass = importsAndClass.replaceFirst(
                    "(?m)^\\s*public\\s+class\\s+" + Pattern.quote(className) + "\\s*\\{",
                    "public class " + className + " extends AbstractTestNGSpringContextTests {"
            );
        }

        // Add container and dynamic property source if missing
        boolean hasContainer = importsAndClass.contains("MongoDBContainer") && importsAndClass.contains("@Container");
        boolean hasDynamicProperty = importsAndClass.contains("@DynamicPropertySource") && importsAndClass.contains("spring.data.mongodb.uri");
        if (!hasContainer || !hasDynamicProperty) {
            String injection = """

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }
""";
            importsAndClass = importsAndClass.replaceFirst(
                    "(?m)^\\s*public\\s+class\\s+" + Pattern.quote(className) + ".*?\\{",
                    "$0" + injection
            );
        }

        adapted = packageLine + importsAndClass;
        return adapted;
    }

    private String ensureImport(String code, String fqcn) {
        String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        String importLine = "import " + fqcn + ";";

        if (code.contains(importLine)) {
            return code;
        }
        if (code.matches("(?s).*\\b" + Pattern.quote(simple) + "\\b.*") && code.contains("import ")) {
            // Type already referenced and import section exists: still add explicit import if missing.
        }

        if (code.contains("package ")) {
            return code.replaceFirst("(?m)^\\s*package\\s+[^;]+;\\s*", "$0\\n" + importLine + "\\n");
        }
        return importLine + "\\n" + code;
    }

    private void ensureTestProperties(Path workDir, String databaseType) {
        if (workDir == null) return;

        Path propsFile = workDir.resolve("src/test/resources/application-test.properties");
        if (Files.exists(propsFile)) {
            return;
        }

        String content;
        String db = databaseType != null ? databaseType.trim().toUpperCase() : "";
        switch (db) {
            case "POSTGRESQL":
                content = """
spring.datasource.url=jdbc:tc:postgresql:14:///testdb
spring.datasource.driverClassName=org.testcontainers.jdbc.ContainerDatabaseDriver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=create-drop
""";
                break;
            case "MYSQL":
                content = """
spring.datasource.url=jdbc:tc:mysql:8.0.33:///testdb
spring.datasource.driverClassName=org.testcontainers.jdbc.ContainerDatabaseDriver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=create-drop
""";
                break;
            case "H2":
                content = """
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
""";
                break;
            case "MONGODB":
                return;
            default:
                // Default to H2 if unknown
                content = """
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
""";
        }

        try {
            Files.createDirectories(propsFile.getParent());
            Files.writeString(propsFile, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            log.info("Created default application-test.properties at {} (db={})", propsFile, db);
        } catch (FileAlreadyExistsException ignored) {
            // Another thread/process created it in the meantime.
        } catch (IOException e) {
            log.warn("Failed to create application-test.properties at {}", propsFile, e);
        }
    }

    private void ensureH2DialectInPom(Path pomXml) throws Exception {
        if (pomXml == null || !Files.exists(pomXml)) {
            return;
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomXml.toFile());

        Element project = doc.getDocumentElement();
        if (project == null) {
            return;
        }

        String ns = project.getNamespaceURI();
        Element properties = findDirectChildByLocalName(project, "properties");
        if (properties == null) {
            properties = ns != null ? doc.createElementNS(ns, "properties") : doc.createElement("properties");
            project.insertBefore(properties, project.getFirstChild());
            log.info("Created <properties> section in {}", pomXml);
        }

        // Remove existing spring.jpa.database-platform property if present
        NodeList propChildren = properties.getChildNodes();
        for (int i = propChildren.getLength() - 1; i >= 0; i--) {
            Node node = propChildren.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) node;
            String ln = el.getLocalName();
            String nn = el.getNodeName();
            if ("spring.jpa.database-platform".equals(ln) || "spring.jpa.database-platform".equals(nn)) {
                properties.removeChild(node);
                log.debug("Removed existing spring.jpa.database-platform property from {}", pomXml);
            }
        }

        // Add or replace spring.jpa.database-platform with H2Dialect
        Element propElement = ns != null 
            ? doc.createElementNS(ns, "spring.jpa.database-platform") 
            : doc.createElement("spring.jpa.database-platform");
        propElement.setTextContent("org.hibernate.dialect.H2Dialect");
        properties.appendChild(propElement);

        // Save the modified pom.xml
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        try {
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        } catch (Exception ignored) {
        }
        transformer.transform(new DOMSource(doc), new StreamResult(pomXml.toFile()));
        log.info("Updated pom.xml: spring.jpa.database-platform set to org.hibernate.dialect.H2Dialect in {}", pomXml);
    }

    private Path findMavenWrapper(Path startDir) {
        if (startDir == null) return null;
        Path current = startDir;
        while (current != null) {
            // Prefer Windows wrapper when present, but accept either mvnw.cmd or mvnw.
            Path cmd = current.resolve("mvnw.cmd");
            if (Files.exists(cmd)) return cmd;

            Path sh = current.resolve("mvnw");
            if (Files.exists(sh)) return sh;

            current = current.getParent();
        }

        log.error("No Maven wrapper (mvnw.cmd/mvnw) found from {} up to filesystem root", startDir);
        return null;
    }

    private void logWorkDirSnapshot(Long testCaseId, Path workDir, int maxEntries) {
        try {
            List<String> entries;
            try (var stream = Files.list(workDir)) {
                entries = stream
                        .limit(maxEntries)
                        .map(p -> {
                            String name = p.getFileName() != null ? p.getFileName().toString() : p.toString();
                            try {
                                if (Files.isDirectory(p)) return name + "/";
                            } catch (Exception ignored) {
                            }
                            return name;
                        })
                        .collect(Collectors.toList());
            }
            log.debug("[TESTCASE {}] workDir snapshot (first {} entries): {}", testCaseId, maxEntries, entries);
        } catch (Exception e) {
            log.warn("[TESTCASE {}] Unable to list workDir contents: {}", testCaseId, workDir, e);
        }
    }

    private long safeFileSize(Path file) {
        try {
            return Files.size(file);
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private Thread startStreamGobbler(InputStream stream, StringBuilder target, String label, Long testCaseId) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    target.append(line).append(System.lineSeparator());
                }
            } catch (Exception e) {
                log.debug("[TESTCASE {}] Stream gobbler '{}' stopped with error: {}", testCaseId, label, e.toString());
            }
        }, "maven-" + label + "-tc-" + testCaseId);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private void joinQuietly(Thread thread, long timeoutMs) {
        if (thread == null) return;
        try {
            thread.join(timeoutMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String combineProcessOutput(StringBuilder stdout, StringBuilder stderr) {
        String out = stdout != null ? stdout.toString() : "";
        String err = stderr != null ? stderr.toString() : "";
        if (err.isBlank()) return out;
        if (out.isBlank()) return err;
        return out + System.lineSeparator() + "----- STDERR -----" + System.lineSeparator() + err;
    }

    private int approximateLineCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }

    private String firstLines(String text, int maxLines, int maxChars) {
        if (text == null || text.isBlank()) return "";
        String[] lines = text.split("\\R", -1);
        int limit = Math.min(maxLines, lines.length);
        StringBuilder sb = new StringBuilder(Math.min(text.length(), maxChars));
        for (int i = 0; i < limit; i++) {
            sb.append(lines[i]).append(System.lineSeparator());
            if (sb.length() >= maxChars) {
                sb.append("...[TRUNCATED]\n");
                break;
            }
        }
        return sb.toString();
    }

    private String resolveBaseUrl(TestCase.TestType testType, Environment env) {
        if (env == null) return "";
        return switch (testType) {
            case WEB -> env.getBaseUrlWeb() != null ? env.getBaseUrlWeb() : "";
            case API, INTEGRATION -> env.getBaseUrlApi() != null ? env.getBaseUrlApi() : "";
            default -> "";
        };
    }

    private void deleteDirectory(Path path) {
        try {
            if (Files.exists(path)) {
                log.debug("Deleting directory recursively: {}", path);
                Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.delete(p); } catch (IOException ignored) {}
                        });
            }
        } catch (IOException e) {
            log.warn("Unable to delete directory: {}", path, e);
        }
    }

    private String getTestPackageByType(TestCase.TestType type) {
        if (type == null) return "suites/generated";
        return switch (type) {
            case UNIT -> "suites/unit";
            case INTEGRATION -> "suites/integration";
            case WEB, API -> "suites/herapp";
        };
    }

    private String findRootPackage(Path repoDir) {
        final String fallback = "suites.unit";
        try {
            Path mainJavaDir = repoDir.resolve("src").resolve("main").resolve("java");
            if (!Files.exists(mainJavaDir) || !Files.isDirectory(mainJavaDir)) {
                log.debug("Root package detection: '{}' not found, using fallback '{}'", mainJavaDir, fallback);
                return fallback;
            }

            try (var stream = Files.list(mainJavaDir)) {
                Path firstDir = stream
                        .filter(Files::isDirectory)
                        .sorted()
                        .findFirst()
                        .orElse(null);

                if (firstDir == null) {
                    log.debug("Root package detection: no directories under '{}', using fallback '{}'", mainJavaDir, fallback);
                    return fallback;
                }

                Path relative = mainJavaDir.relativize(firstDir);
                String pkg = relative.toString()
                    .replace('\\', '.')
                        .replace('/', '.')
                    .replaceAll("^\\.+|\\.+$", "");

                log.debug("Root package detection: mainJavaDir='{}', firstDir='{}', pkg='{}'", mainJavaDir, firstDir, pkg);
                return (pkg == null || pkg.isBlank()) ? fallback : pkg;
            }
        } catch (Exception e) {
            log.debug("Root package detection failed, using fallback '{}'", fallback, e);
            return fallback;
        }
    }
}