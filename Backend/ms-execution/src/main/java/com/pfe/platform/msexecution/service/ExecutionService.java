package com.pfe.platform.msexecution.service;

import com.pfe.platform.msexecution.entity.*;
import com.pfe.platform.msexecution.repository.*;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
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

    private static final ThreadLocal<Path> CURRENT_EXECUTION_DIR = new ThreadLocal<>();

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

        @Value("${execution.screenshots-dir:screenshots}")
        private String screenshotsDir;
        private static final String DEP_TESTCONTAINERS_ARTIFACT_MYSQL = "mysql";
        private static final String DEP_TESTCONTAINERS_ARTIFACT_MONGODB = "mongodb";
        private static final String DEP_TESTCONTAINERS_ARTIFACT_JUNIT_JUPITER = "junit-jupiter";

        private static final String DEP_SELENIUM_GROUP = "org.seleniumhq.selenium";
        private static final String DEP_SELENIUM_ARTIFACT = "selenium-java";
        private static final String DEP_SELENIUM_VERSION = "4.18.1";

        private static final String DEP_HTMLUNIT_ARTIFACT = "htmlunit-driver";
        private static final String DEP_HTMLUNIT_VERSION = "4.13.0";

        private static final String DEP_WDM_GROUP = "io.github.bonigarcia";
        private static final String DEP_WDM_ARTIFACT = "webdrivermanager";
        private static final String DEP_WDM_VERSION = "5.8.0";

        private static final String DEP_APPIUM_GROUP = "io.appium";
        private static final String DEP_APPIUM_ARTIFACT = "java-client";
        private static final String DEP_APPIUM_VERSION = "9.2.2";

        private static final String DEP_REST_ASSURED_GROUP = "io.rest-assured";
        private static final String DEP_REST_ASSURED_ARTIFACT = "rest-assured";
        private static final String DEP_REST_ASSURED_VERSION = "5.4.0";

    private final CampaignRepository campaignRepository;
    private final CampaignTestCaseRepository campaignTestCaseRepository;
    private final ExecutionResultRepository executionResultRepository;
    private final LlmAnalysisService llmAnalysisService;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestSuiteRepository testSuiteRepository;
    private final com.pfe.platform.msexecution.service.ReportStorageService reportStorageService;
    private final ScriptRetryService scriptRetryService;

    @Value("${execution.temp-dir:}")
    private String tempDirConfig;

    @Value("${execution.maven-timeout-minutes:15}")
    private long mavenTimeoutMinutes;

    @Value("${github.token:}")
    private String githubToken;

    @Async
    public void runCampaign(Long campaignId) {
        runCampaign(campaignId, null);
    }

    @Async
    public void runCampaign(Long campaignId, List<Long> selectedTestCaseIds) {
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

        Path repoDir = null;
        try {
            log.info("[CAMPAIGN {}] Preparing base repository (project-level)", campaignId);
            repoDir = prepareRepository(project, campaign, env);
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

        log.info("Fetching test cases for campaign ID: {}", campaignId);
        List<CampaignTestCase> ctcList = campaignTestCaseRepository
                .findByCampaignIdOrderByExecutionOrder(campaignId);
        log.info("Found {} test cases for campaign", ctcList.size());

        if (selectedTestCaseIds != null && !selectedTestCaseIds.isEmpty()) {
            java.util.Set<Long> selectedSet = new java.util.HashSet<>(selectedTestCaseIds);
            ctcList = ctcList.stream()
                    .filter(ctc -> selectedSet.contains(ctc.getTestCaseId()))
                    .toList();
            log.info("[CAMPAIGN {}] Filtered to {} selected test cases (out of total)", campaignId, ctcList.size());
        }

        if (ctcList.isEmpty()) {
            log.warn("No test cases found for campaign! Marking as finished successfully.");
        }

        // Tri par priorité d'exécution
        if (!ctcList.isEmpty()) {
            List<Long> tcIds = ctcList.stream().map(CampaignTestCase::getTestCaseId).toList();
            Map<Long, TestCase> tcById = testCaseRepository.findAllById(tcIds).stream()
                    .collect(java.util.stream.Collectors.toMap(TestCase::getId, t -> t));
            ctcList = ctcList.stream()
                    .sorted(java.util.Comparator.comparingInt(
                            (CampaignTestCase ctc) -> computeExecutionScore(tcById.get(ctc.getTestCaseId()))
                    ).reversed())
                    .toList();
            log.info("[CAMPAIGN {}] Test cases sorted by execution priority (score: {})",
                    campaignId,
                    ctcList.stream()
                           .map(ctc -> tcById.get(ctc.getTestCaseId()))
                           .filter(java.util.Objects::nonNull)
                           .map(tc -> tc.getId() + "→" + computeExecutionScore(tc))
                           .collect(java.util.stream.Collectors.joining(", ")));
        }

        Map<String, Path> suiteRepoMap = new HashMap<>();
        Map<Long, Path> suiteTemplateMap = new HashMap<>();

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

                if (Boolean.FALSE.equals(tc.getActive())) {
                    log.info("[CAMPAIGN {}] TestCase {} is inactive, skipping", campaignId, tc.getId());
                    continue;
                }

                log.info(
                        "[CAMPAIGN {}] TestCase {} => type={}, generated={}",
                        campaignId,
                        tc.getId(),
                        tc.getType(),
                        Boolean.TRUE.equals(tc.getGenerated())
                );

                TestCase.TestType testType = tc.getType() != null ? tc.getType() : TestCase.TestType.WEB;

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
                    // inherit from environment
                    String effectiveSuiteGitUrl = (suite != null && suite.getGitRepoUrl() != null && !suite.getGitRepoUrl().isBlank())
                            ? suite.getGitRepoUrl()
                            : ((env != null && env.getGitRepoUrl() != null && !env.getGitRepoUrl().isBlank()
                                && (testType == TestCase.TestType.UNIT || testType == TestCase.TestType.INTEGRATION))
                                ? env.getGitRepoUrl()
                                : null);
                    if (effectiveSuiteGitUrl != null) {
                        if (suite != null && (suite.getGitRepoUrl() == null || suite.getGitRepoUrl().isBlank())) {
                            // patch suite object so cache key uses env url
                            suite.setGitRepoUrl(effectiveSuiteGitUrl);
                            suite.setGitBranch(env.getGitBranch() != null ? env.getGitBranch() : "main");
                        }
                    }
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
                    } else if (suite != null && (testType == TestCase.TestType.WEB || testType == TestCase.TestType.API)) {
                        // E2E suites without gitRepoUrl must use the built-in AI template as working directory.
                        try {
                            Long suiteKey = suite.getId();
                            workDir = suiteTemplateMap.computeIfAbsent(suiteKey, key -> {
                                try {
                                    log.info(
                                            "[CAMPAIGN {}] Preparing built-in template for suiteId={} (no gitRepoUrl)",
                                            campaignId,
                                            suiteKey
                                    );
                                    Path prepared = prepareBuiltInTemplate();
                                    log.info("Built-in template prepared for suiteId={} at {}", suiteKey, prepared);
                                    return prepared;
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        } catch (RuntimeException e) {
                            log.error("Failed to prepare built-in template for suite {}, falling back to project repo", suiteId, e);
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
                // Retry pour les tests flaky
                ExecutionResult result = executeRealTest(tc, env, workDir, campaignId);
                if (Boolean.TRUE.equals(tc.getFlaky())
                        && (result.getStatus() == ExecutionResult.ResultStatus.FAILURE
                            || result.getStatus() == ExecutionResult.ResultStatus.ERROR)) {
                    log.warn("[CAMPAIGN {}] TestCase {} is flaky and failed — retrying (attempt 2/2)", campaignId, tc.getId());
                    result = executeRealTest(tc, env, workDir, campaignId);
                }
                result.setCampaignId(campaignId);
                result.setTestCaseId(tc.getId());

                log.info("Test result for {}: status={}, error={}",
                        tc.getId(), result.getStatus(), result.getErrorMessage());

                executionResultRepository.save(result);
                // If test failed/errored, try to persist the latest screenshot to the permanent folder
                if (result.getStatus() == ExecutionResult.ResultStatus.FAILURE || result.getStatus() == ExecutionResult.ResultStatus.ERROR) {
                    try {
                        Path latest = findLatestScreenshot(workDir);
                        if (latest != null) {
                            copyScreenshotToPermanent(latest, result);
                            executionResultRepository.save(result);
                        }
                    } catch (Exception e) {
                        log.warn("Unable to persist screenshot for test {}: {}", tc.getId(), e.getMessage());
                    }
                }
                String scriptCode = resolveScriptCode(tc, workDir);
                String analysis = llmAnalysisService.analyze(
                    scriptCode != null ? scriptCode : "Script non disponible",
                    result.getLogs() != null ? result.getLogs() : "Logs non disponibles",
                    result.getStatus() != null ? result.getStatus().name() : "UNKNOWN",
                    result.getErrorMessage() != null ? result.getErrorMessage() : ""
                );
                result.setAiAnalysis(analysis);
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
            // Génération du rapport PDF
            try {
                reportStorageService.generateAndStoreAsync(campaignId, null);
            } catch (Exception e) {
                log.warn("Failed to trigger async report generation for campaign {}: {}", campaignId, e.getMessage());
            }
            log.info("=== CAMPAIGN EXECUTION COMPLETED FOR ID: {} ===", campaignId);
        } catch (Exception e) {
            log.error("FATAL: Campaign execution failed", e);
            finishWithError(campaign, "Erreur lors de l'exécution : " + e.getMessage());
        } finally {
            // Clean up cloned suite repositories
            log.info("[CAMPAIGN {}] Cleaning up suite repositories (count={})", campaignId, suiteRepoMap.size());
            suiteRepoMap.values().forEach(this::deleteDirectory);
            log.info("Cleaned up {} cloned suite repositories", suiteRepoMap.size());

            // Clean up extracted suite templates
            log.info("[CAMPAIGN {}] Cleaning up suite templates (count={})", campaignId, suiteTemplateMap.size());
            suiteTemplateMap.values().forEach(this::deleteDirectory);
            log.info("Cleaned up {} extracted suite templates", suiteTemplateMap.size());

            // Clean up project repository
            if (repoDir != null) {
                log.info("[CAMPAIGN {}] Cleaning up base repository at {}", campaignId, repoDir);
                deleteDirectory(repoDir);
                log.info("Cleaned up project repository at: {}", repoDir);
            }
        }
    }

    private void finishWithError(Campaign campaign, String errorMessage) {
        campaign.setStatus(Campaign.CampaignStatus.FINISHED_WITH_ERRORS);
        campaign.setProgress(100);
        campaign.setCurrentStep(errorMessage);
        campaign.setFinishedAt(LocalDateTime.now());
        campaignRepository.save(campaign);
    }

    private String resolveScriptCode(TestCase tc, Path workDir) {
        if (tc == null) {
            return null;
        }

        String generatedCode = tc.getGeneratedCode();
        if (generatedCode != null && !generatedCode.isBlank()) {
            return generatedCode;
        }

        String scriptPath = tc.getScriptPath();
        if (scriptPath == null || scriptPath.isBlank() || workDir == null) {
            return null;
        }

        try {
            Path scriptFile = workDir.resolve("src/test/java").resolve(scriptPath).normalize();
            if (Files.exists(scriptFile) && Files.isRegularFile(scriptFile)) {
                return Files.readString(scriptFile, StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            log.warn("Unable to read script source for test {}: {}", tc.getId(), ex.getMessage());
        }

        return null;
    }

    // Préparation du dépôt d'exécution
    private Path prepareRepository(Project project, Campaign campaign, Environment env) throws IOException, GitAPIException {
        // gitRepoUrl depuis l'environnement, fallback sur le projet
        String gitUrl = (env != null && env.getGitRepoUrl() != null && !env.getGitRepoUrl().isBlank())
                ? env.getGitRepoUrl()
                : project.getGitRepoUrl();

        if (gitUrl == null || gitUrl.isBlank() || "ai-builtin".equalsIgnoreCase(gitUrl)) {
            log.info("Using built-in AI test template (no Git URL configured on environment or project)");
            return prepareBuiltInTemplate();
        }

        // Branche depuis l'environnement
        String branch = (env != null && env.getGitBranch() != null && !env.getGitBranch().isBlank())
                ? env.getGitBranch()
                : "main";

        log.info("Cloning repository from: {} on branch: {}", gitUrl, branch);
        return cloneRepository(gitUrl, branch);
    }

    private Path prepareBuiltInTemplate() throws IOException {
        // 1) Créer un répertoire temporaire
        Path execDir = createTempDir("exec-");
        log.info("Preparing built-in AI template into temp dir: {}", execDir);

        // 2) Dev-friendly mode: if the directory resource exists on disk (exploded classes), copy it directly.
        Resource dirResource = new ClassPathResource("ai-test-template");
        if (dirResource.exists()) {
            try {
                Path sourceDir = dirResource.getFile().toPath();
                if (Files.isDirectory(sourceDir)) {
                    log.info("Copying built-in AI template from classpath directory: {}", sourceDir);
                    copyDirectory(sourceDir, execDir);
                    Path resolvedRoot = resolveTemplateRoot(execDir);
                    log.info("AI template prepared at: {}", resolvedRoot);
                    return resolvedRoot;
                }
            } catch (Exception ignored) {
                // When packaged as a jar, getFile() typically fails; fall back to zip extraction.
            }
        }

        // 3) Default mode: unzip from the embedded zip resource (works in a packaged jar)
        Resource zipResource = new ClassPathResource("ai-test-template.zip");
        if (!zipResource.exists()) {
            throw new RuntimeException("Template IA introuvable (ai-test-template.zip / ai-test-template)");
        }

        unzipTemplate(zipResource, execDir);

        Path resolvedRoot = resolveTemplateRoot(execDir);
        log.info("AI template prepared at: {}", resolvedRoot);
        return resolvedRoot;
    }

    private void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
        try (var stream = Files.walk(sourceDir)) {
            for (Path sourcePath : (Iterable<Path>) stream::iterator) {
                Path relative = sourceDir.relativize(sourcePath);
                if (relative.toString().isEmpty()) continue;
                Path targetPath = targetDir.resolve(relative);

                if (Files.isDirectory(sourcePath)) {
                    ensureDirectory(targetPath);
                } else {
                    ensureDirectory(targetPath.getParent());
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void unzipTemplate(Resource zipResource, Path execDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(zipResource.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String rawName = entry.getName();
                if (rawName == null || rawName.isBlank()) {
                    zis.closeEntry();
                    continue;
                }

                // Normalize separators: ZIPs should use '/', but be defensive.
                String entryName = rawName.replace('\\', '/');
                while (entryName.startsWith("/")) {
                    entryName = entryName.substring(1);
                }
                if (entryName.isBlank()) {
                    zis.closeEntry();
                    continue;
                }

                Path entryPath = execDir.resolve(entryName).normalize();
                Path execRoot = execDir.normalize();
                if (!entryPath.startsWith(execRoot)) {
                    // Prevent Zip Slip
                    throw new IOException("Invalid ZIP entry path: " + rawName);
                }

                boolean isDirectory = entry.isDirectory() || entryName.endsWith("/");
                if (isDirectory) {
                    ensureDirectory(entryPath);
                } else {
                    // If parent path exists as a file (can happen with malformed zips like src/test as a file), fix it.
                    ensureDirectory(entryPath.getParent());

                    // If a directory is expected later but a placeholder file already exists, we'll overwrite safely here.
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private void ensureDirectory(Path dir) throws IOException {
        if (dir == null) return;

        // Fix file-vs-directory collisions (including parent components).
        // Example: a malformed ZIP may create a file at 'src/test', then later we need to create 'src/test/java'.
        for (int attempt = 0; attempt < 2; attempt++) {
            if (Files.exists(dir) && !Files.isDirectory(dir)) {
                Files.delete(dir);
            }

            try {
                Files.createDirectories(dir);
                return;
            } catch (java.nio.file.FileAlreadyExistsException e) {
                // Find the first ancestor that exists as a file and delete it, then retry.
                Path cursor = dir;
                while (cursor != null) {
                    if (Files.exists(cursor) && !Files.isDirectory(cursor)) {
                        Files.delete(cursor);
                        break;
                    }
                    cursor = cursor.getParent();
                }
                if (attempt == 1) {
                    throw e;
                }
            }
        }
    }

    private Path resolveTemplateRoot(Path extractedDir) {
        if (extractedDir == null) return null;
        if (Files.exists(extractedDir.resolve("pom.xml"))) {
            return extractedDir;
        }
        try (var stream = Files.list(extractedDir)) {
            List<Path> children = stream
                    .filter(Files::isDirectory)
                    .sorted()
                    .collect(Collectors.toList());
            if (children.size() == 1) {
                Path candidate = children.get(0);
                if (Files.exists(candidate.resolve("pom.xml"))) {
                    return candidate;
                }
            }
        } catch (Exception ignored) {
        }
        return extractedDir;
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
        // Authentification PAT dans l'URL pour les repos privés
        String effectiveUrl = (githubToken != null && !githubToken.isBlank() && repoUrl.startsWith("https://"))
                ? repoUrl.replace("https://", "https://oauth2:" + githubToken + "@")
                : repoUrl;
        log.info("Cloning Git repo => url='{}', branch='{}', dest='{}'", repoUrl, branch, dir);
        Git.cloneRepository()
                .setURI(effectiveUrl)
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

            // Vérification du répertoire de travail
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

            CURRENT_EXECUTION_DIR.set(workDir);

            // Création du dossier screenshots
            try {
                Files.createDirectories(workDir.resolve("screenshots"));
            } catch (Exception e) {
                log.debug("[TESTCASE {}] Unable to pre-create screenshots dir under {}: {}", tc.getId(), workDir, e.toString());
            }

            // Écriture du fichier testData
            Path testDataFile = null;
            if (tc.getTestData() != null && !tc.getTestData().isBlank()) {
                try {
                    testDataFile = workDir.resolve("test-data-" + tc.getId() + ".json");
                    Files.writeString(testDataFile, tc.getTestData(), StandardCharsets.UTF_8);
                    log.info("[TESTCASE {}] testData written to {}", tc.getId(), testDataFile);
                } catch (Exception e) {
                    log.warn("[TESTCASE {}] Failed to write testData file: {}", tc.getId(), e.getMessage());
                    testDataFile = null;
                }
            }

            Path pom = workDir.resolve("pom.xml");
            if (!Files.exists(pom)) {
                log.warn("[TESTCASE {}] No pom.xml found at workDir={} (pom.xml={})", tc.getId(), workDir, pom);
            } else {
                log.debug("[TESTCASE {}] pom.xml found: {} (size={} bytes)", tc.getId(), pom, safeFileSize(pom));
                TestCase.TestType pomType = tc.getType();
                log.info("[TESTCASE {}] Ensuring required test dependencies in pom.xml for type={}...", tc.getId(), pomType);
                ensureTestDependencies(pom, pomType, tc.getDatabaseType());
                log.info("[TESTCASE {}] pom.xml dependency check completed", tc.getId());
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
                String codeToWrite = cleanCode;
                if (tc.getType() == TestCase.TestType.INTEGRATION) {
                    codeToWrite = ensureSpringBootTestConfig(codeToWrite, workDir);
                    codeToWrite = stripInlineTestPropertySource(codeToWrite);
                }
                Files.writeString(testFile, codeToWrite);
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

            // Vérification du Maven wrapper
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

            // Configuration du profil de test pour les tests d'intégration
            TestCase.TestType effectiveType = tc.getType() != null ? tc.getType() : TestCase.TestType.WEB;
            if (effectiveType == TestCase.TestType.INTEGRATION) {
                // databaseType: priorité test case > environnement
                String dbType = (tc.getDatabaseType() != null && !tc.getDatabaseType().isBlank())
                        ? tc.getDatabaseType()
                        : (env != null ? env.getDatabaseType() : null);
                if (dbType == null || dbType.isBlank()) {
                    String message = "databaseType is required for INTEGRATION tests (set on test case or environment)";
                    log.error("[TESTCASE {}] {}", tc.getId(), message);
                    result.setDurationMs(System.currentTimeMillis() - start);
                    result.setStatus(ExecutionResult.ResultStatus.ERROR);
                    result.setErrorMessage(message);
                    result.setLogs(message);
                    return result;
                }

                // Ensure dependencies for integration tests (DB-specific additions included)
                ensureTestDependencies(pom, effectiveType, dbType); // dbType already resolved above

                // Apply DB-specific test properties
                ensureTestProperties(workDir, dbType);

                // Dialecte H2 dans le pom si nécessaire
                if ("H2".equalsIgnoreCase(dbType)) {
                    try {
                        ensureH2DialectInPom(pom);
                    } catch (Exception e) {
                        log.warn("[TESTCASE {}] Failed to ensure H2 dialect in pom.xml: {}", tc.getId(), e.getMessage());
                    }
                }
            }

            List<String> command = buildMavenCommand(tc, env, workDir, className, wrapper, testDataFile);
            // Injection de la version applicative
            if (campaignId != null) {
                Campaign campaignForVersion = campaignRepository.findById(campaignId).orElse(null);
                if (campaignForVersion != null && campaignForVersion.getAppVersion() != null
                        && !campaignForVersion.getAppVersion().isBlank()) {
                    command.add("-Dapp.version=" + campaignForVersion.getAppVersion());
                }
            }

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
            // Timeout par test ou global
            long effectiveTimeoutMinutes = (tc.getMaxDurationSeconds() != null && tc.getMaxDurationSeconds() > 0)
                    ? Math.max(1, (long) Math.ceil(tc.getMaxDurationSeconds() / 60.0))
                    : mavenTimeoutMinutes;
            log.info("[TESTCASE {}] Timeout set to {} minute(s) (maxDurationSeconds={})",
                    tc.getId(), effectiveTimeoutMinutes, tc.getMaxDurationSeconds());
            boolean finished = process.waitFor(effectiveTimeoutMinutes, TimeUnit.MINUTES);
            long waitForCompletedTime = System.currentTimeMillis();
            log.debug(
                    "[TESTCASE {}] process.waitFor() completed (elapsed={}ms, finished={})",
                    tc.getId(),
                    (waitForCompletedTime - processStartTime),
                    finished
            );

            // Attente fin de lecture des flux
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
                result.setErrorMessage("Maven timeout after " + effectiveTimeoutMinutes + " minutes");
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

            // Parsing des rapports Surefire
            try {
                String methodResults = parseSurefireReports(workDir);
                if (methodResults != null) {
                    result.setTestMethodResults(methodResults);
                }
            } catch (Exception e) {
                log.warn("[TESTCASE {}] Failed to parse Surefire reports: {}", tc.getId(), e.getMessage());
            }

            if (exitCode != 0) {
                result.setStatus(ExecutionResult.ResultStatus.FAILURE);
                result.setErrorMessage("Maven exit code : " + exitCode);
                log.error("[TESTCASE {}] Maven FAILURE exitCode={} (durationMs={})", tc.getId(), exitCode, duration);
                log.error("[TESTCASE {}] Maven output (first 500 lines, truncated):\n{}", tc.getId(), firstLines(combinedOutput, 500, 20_000));

                // === AUTO-RETRY M3: si test généré + erreur corrigeable, corriger et relancer ===
                if (Boolean.TRUE.equals(tc.getGenerated()) && tc.getGeneratedCode() != null
                        && isRetryableError(combinedOutput)) {
                    int maxRetries = 3;
                    StringBuilder retryLogBuilder = new StringBuilder();
                    String currentScript = tc.getGeneratedCode();
                    String relevantErrors = llmAnalysisService.extractRelevantErrors(combinedOutput);

                    // Phase 2: give the corrector the same grounding as the initial generation —
                    // the real source class under test + the project's class tree (for correct imports).
                    String projectFileTree = buildProjectFileTree(workDir);
                    String sourceClassContent = findSourceClassUnderTest(tc.getGeneratedCode(), workDir);
                    log.info("[TESTCASE {}] Retry context: fileTree={} chars, sourceClass={} chars",
                            tc.getId(),
                            projectFileTree != null ? projectFileTree.length() : 0,
                            sourceClassContent != null ? sourceClassContent.length() : 0);

                    for (int retry = 1; retry <= maxRetries; retry++) {
                        log.info("[TESTCASE {}] AUTO-RETRY {}/{} — L'IA corrige le script...", tc.getId(), retry, maxRetries);

                        Campaign retryStatusCampaign = campaignRepository.findById(campaignId).orElse(null);
                        if (retryStatusCampaign != null) {
                            retryStatusCampaign.setCurrentStep("AI_RETRY:" + tc.getId() + ":" + retry + "/" + maxRetries);
                            campaignRepository.save(retryStatusCampaign);
                        }

                        retryLogBuilder.append("=== Tentative ").append(retry).append("/").append(maxRetries).append(" ===\n");
                        retryLogBuilder.append("Erreur détectée: ").append(relevantErrors.substring(0, Math.min(500, relevantErrors.length()))).append("\n");

                        // Pull the real source of classes named in the errors AND every class the test
                        // instantiates (composite keys, entities built for setup…) so the corrector
                        // stops guessing their constructors/types.
                        String relatedClasses = collectRelevantSources(relevantErrors, currentScript, sourceClassContent, workDir);
                        log.info("[TESTCASE {}] Retry {}: relatedClasses={} chars",
                                tc.getId(), retry, relatedClasses != null ? relatedClasses.length() : 0);

                        // For test (non-compile) failures the real cause (e.g. a business exception
                        // thrown from the service) lives in the surefire report, not in the filtered
                        // [ERROR] lines — feed it to the corrector so it can fix the missing DB/mock setup.
                        String errorContext = relevantErrors;
                        String surefireForRetry = readSurefireFailureDetails(workDir);
                        if (surefireForRetry != null) {
                            errorContext = relevantErrors + "\n\n== Détails surefire (vraie cause de l'échec) ==\n"
                                    + firstLines(surefireForRetry, 60, 4000);
                        }

                        String correctedScript = scriptRetryService.correctScript(
                                currentScript, errorContext, sourceClassContent, relatedClasses, projectFileTree);

                        if (correctedScript == null || correctedScript.isBlank()) {
                            retryLogBuilder.append("Résultat: L'IA n'a pas pu corriger le script.\n\n");
                            log.warn("[TESTCASE {}] Retry {}: LLM returned null correction", tc.getId(), retry);
                            break;
                        }

                        retryLogBuilder.append("Résultat: Script corrigé (").append(correctedScript.length()).append(" chars), relance Maven...\n");

                        // Réécrire le fichier test
                        String subDir = getTestPackageByType(tc.getType());
                        if (subDir == null || subDir.isBlank()) subDir = "suites/generated";
                        var retryClassMatcher = Pattern.compile("\\bclass\\s+(\\w+)").matcher(correctedScript);
                        if (!retryClassMatcher.find()) {
                            retryLogBuilder.append("Résultat: Script corrigé invalide (pas de déclaration de classe).\n\n");
                            break;
                        }
                        String retryClassName = retryClassMatcher.group(1);
                        Path retryTestFile = workDir.resolve("src/test/java").resolve(subDir).resolve(retryClassName + ".java");
                        String retryCodeToWrite = correctedScript;
                        if (tc.getType() == TestCase.TestType.INTEGRATION) {
                            retryCodeToWrite = ensureSpringBootTestConfig(retryCodeToWrite, workDir);
                            retryCodeToWrite = stripInlineTestPropertySource(retryCodeToWrite);
                        }
                        Files.writeString(retryTestFile, retryCodeToWrite);
                        log.info("[TESTCASE {}] Retry {}: corrected script written to {}", tc.getId(), retry, retryTestFile);

                        // Relancer Maven
                        ProcessBuilder retryPb = new ProcessBuilder(command);
                        retryPb.directory(workDir.toFile());
                        retryPb.redirectErrorStream(false);
                        Process retryProcess = retryPb.start();

                        StringBuilder retryStdout = new StringBuilder(32 * 1024);
                        StringBuilder retryStderr = new StringBuilder(16 * 1024);
                        Thread retryOutThread = startStreamGobbler(retryProcess.getInputStream(), retryStdout, "retry-stdout", tc.getId());
                        Thread retryErrThread = startStreamGobbler(retryProcess.getErrorStream(), retryStderr, "retry-stderr", tc.getId());

                        boolean retryFinished = retryProcess.waitFor(effectiveTimeoutMinutes, TimeUnit.MINUTES);
                        joinQuietly(retryOutThread, 10_000);
                        joinQuietly(retryErrThread, 10_000);

                        String retryCombinedOutput = combineProcessOutput(retryStdout, retryStderr);
                        int retryExitCode = retryFinished ? retryProcess.exitValue() : -1;
                        boolean retryCompiles = !isCompilationError(retryCombinedOutput);
                        log.info("[TESTCASE {}] Retry {}: Maven exitCode={}, compiles={}",
                                tc.getId(), retry, retryExitCode, retryCompiles);

                        if (!retryFinished) {
                            retryProcess.destroyForcibly();
                            retryLogBuilder.append("Résultat: Timeout Maven.\n\n");
                            break;
                        }

                        if (retryExitCode == 0) {
                            retryLogBuilder.append("Résultat: SUCCÈS après correction !\n");
                            result.setStatus(ExecutionResult.ResultStatus.SUCCESS);
                            result.setErrorMessage(null);
                            result.setLogs(retryCombinedOutput);
                            // Mettre à jour le code généré avec la version corrigée
                            currentScript = correctedScript;

                            try {
                                String retryMethodResults = parseSurefireReports(workDir);
                                if (retryMethodResults != null) result.setTestMethodResults(retryMethodResults);
                            } catch (Exception ignored) {}

                            log.info("[TESTCASE {}] AUTO-RETRY SUCCESS at attempt {}", tc.getId(), retry);
                            break;
                        } else {
                            retryLogBuilder.append("Résultat: Encore en échec (exit=").append(retryExitCode).append(").\n\n");
                            currentScript = correctedScript;
                            relevantErrors = llmAnalysisService.extractRelevantErrors(retryCombinedOutput);
                            combinedOutput = retryCombinedOutput;
                            result.setLogs(retryCombinedOutput);

                            if (!isRetryableError(retryCombinedOutput)) {
                                retryLogBuilder.append("L'erreur n'est plus corrigeable automatiquement. Arrêt du retry.\n");
                                String surefireDetails = readSurefireFailureDetails(workDir);
                                log.warn("[TESTCASE {}] Retry {}: error no longer auto-retryable "
                                                + "(compiles={}). Stopping.\n-- Maven extract --\n{}\n-- Surefire details --\n{}",
                                        tc.getId(), retry, retryCompiles,
                                        firstLines(relevantErrors, 40, 3000),
                                        surefireDetails != null ? firstLines(surefireDetails, 80, 6000)
                                                : "(aucun fichier surefire-reports trouvé)");
                                break;
                            }
                        }
                    }
                    result.setRetryCount(Math.min(maxRetries, retryLogBuilder.toString().split("=== Tentative").length - 1));
                    result.setRetryLog(retryLogBuilder.toString());

                    // If retries didn't fix it, dump the final generated test so we can see exactly
                    // what the model produced (the temp clone is wiped right after).
                    if (result.getStatus() != ExecutionResult.ResultStatus.SUCCESS) {
                        log.warn("[TESTCASE {}] Final generated test after {} retries (status={}):\n{}",
                                tc.getId(), maxRetries, result.getStatus(),
                                firstLines(currentScript, 250, 9000));
                    }
                }
            }

            // Analyse UX si applicable
            if (tc.getType() == TestCase.TestType.FUNCTIONAL_WEB || tc.getType() == TestCase.TestType.FUNCTIONAL_MOBILE) {
                String testSummary = extractUxSummary(combinedOutput);
                if (testSummary != null && !testSummary.isBlank()) {
                    String analysis = llmAnalysisService.analyzeUx(testSummary, combinedOutput, tc.getType().name());
                    result.setUxAnalysis(analysis);
                    log.info("[TESTCASE {}] UX analysis generated", tc.getId());
                } else {
                    result.setUxAnalysis("TEST_SUMMARY non disponible");
                    log.info("[TESTCASE {}] UX summary not found in logs", tc.getId());
                }
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

            // Capture d'écran pour les échecs E2E
            attachLatestScreenshotIfPresent(result, workDir, start);

            CURRENT_EXECUTION_DIR.remove();

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

        if (testType == null) {
            testType = TestCase.TestType.WEB;
        }

        if (testType == TestCase.TestType.UNIT || testType == TestCase.TestType.INTEGRATION) {
            changed |= ensureDependency(doc, dependencies, ns, pomXml,
                DEP_MOCKITO_GROUP, DEP_MOCKITO_ARTIFACT, DEP_MOCKITO_VERSION, "test");
        }

        if (testType == TestCase.TestType.WEB || testType == TestCase.TestType.FUNCTIONAL_WEB) {
            changed |= ensureDependency(doc, dependencies, ns, pomXml,
                DEP_SELENIUM_GROUP, DEP_SELENIUM_ARTIFACT, DEP_SELENIUM_VERSION, "test");
            changed |= ensureDependency(doc, dependencies, ns, pomXml,
                DEP_SELENIUM_GROUP, DEP_HTMLUNIT_ARTIFACT, DEP_HTMLUNIT_VERSION, "test");
            changed |= ensureDependency(doc, dependencies, ns, pomXml,
                DEP_WDM_GROUP, DEP_WDM_ARTIFACT, DEP_WDM_VERSION, "test");
        }

        if (testType == TestCase.TestType.FUNCTIONAL_MOBILE) {
            changed |= ensureDependency(doc, dependencies, ns, pomXml,
                DEP_APPIUM_GROUP, DEP_APPIUM_ARTIFACT, DEP_APPIUM_VERSION, "test");
        }

        if (testType == TestCase.TestType.API) {
            changed |= ensureDependency(doc, dependencies, ns, pomXml,
                DEP_REST_ASSURED_GROUP, DEP_REST_ASSURED_ARTIFACT, DEP_REST_ASSURED_VERSION, "test");
        }

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
        return buildMavenCommand(tc, env, repoDir, className, wrapper, null);
    }

    private List<String> buildMavenCommand(TestCase tc, Environment env, Path repoDir, String className, Path wrapper, Path testDataFile) {
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

        // databaseType: priorité test case > environnement
        String effectiveDbType = (tc.getDatabaseType() != null && !tc.getDatabaseType().isBlank())
                ? tc.getDatabaseType()
                : (env != null ? env.getDatabaseType() : null);

        // Profil Spring actif
        String effectiveProfile = (tc.getSpringProfile() != null && !tc.getSpringProfile().isBlank())
                ? tc.getSpringProfile()
                : "test";

        switch (testType) {
            case UNIT:
                command.add("-Dtest.layer=unit");
                command.add("-Dspring.profiles.active=" + effectiveProfile);
                break;
            case INTEGRATION:
                command.add("-Dtest.layer=integration");
                if (effectiveDbType != null && effectiveDbType.trim().equalsIgnoreCase("H2")) {
                    command.add("-Dspring.jpa.database-platform=org.hibernate.dialect.H2Dialect");
                }
                if (baseUrl != null && !baseUrl.isBlank()) {
                    command.add("-DBASE_URL=" + baseUrl);
                }
                command.add("-Dspring.profiles.active=" + effectiveProfile);
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

        // Chemin du fichier testData
        if (testDataFile != null && Files.exists(testDataFile)) {
            command.add("-Dtest.data.file=" + testDataFile.toAbsolutePath());
        }

        // Version applicative (depuis la campagne)

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

    /** Score de priorité d'exécution : riskLevel (x10) + priority inversée. */
    private int computeExecutionScore(TestCase tc) {
        if (tc == null) return 0;
        int riskScore = switch (tc.getRiskLevel() != null ? tc.getRiskLevel().toUpperCase() : "") {
            case "CRITICAL" -> 4;
            case "HIGH"     -> 3;
            case "MEDIUM"   -> 2;
            case "LOW"      -> 1;
            default         -> 0;
        };
        int p = tc.getPriority() != null && tc.getPriority() > 0 ? tc.getPriority() : 0;
        int priorityScore = p > 0 ? Math.max(0, 11 - p) : 0;
        return riskScore * 10 + priorityScore;
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

    /**
     * Builds a flat list of fully-qualified class names from the cloned project's main sources,
     * so the corrector can resolve correct imports (fixes "cannot find symbol" / "package does not exist").
     */
    private String buildProjectFileTree(Path repoRoot) {
        if (repoRoot == null) return null;
        Path srcRoot = repoRoot.resolve("src/main/java");
        if (!Files.isDirectory(srcRoot)) return null;
        try (var stream = Files.walk(srcRoot)) {
            java.util.List<String> fqns = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(p -> srcRoot.relativize(p).toString()
                            .replace(java.io.File.separatorChar, '.')
                            .replaceAll("\\.java$", ""))
                    .sorted()
                    .limit(400)
                    .collect(java.util.stream.Collectors.toList());
            if (fqns.isEmpty()) return null;
            String tree = String.join("\n", fqns);
            return tree.length() > 12_000 ? tree.substring(0, 12_000) : tree;
        } catch (Exception e) {
            log.warn("[buildProjectFileTree] Failed to scan {}: {}", srcRoot, e.getMessage());
            return null;
        }
    }

    /**
     * Locates the real source class under test (parsed from the generated test's @InjectMocks /
     * @Autowired type) inside the cloned repo and returns its content (capped). Gives the corrector
     * the actual setters/getters/method names instead of guessing.
     */
    private String findSourceClassUnderTest(String testCode, Path repoRoot) {
        if (testCode == null || repoRoot == null) return null;
        String className = null;
        java.util.regex.Matcher inj = Pattern.compile("@InjectMocks\\s+(?:private\\s+)?(\\w+)\\s+\\w+")
                .matcher(testCode);
        if (inj.find()) {
            className = inj.group(1);
        } else {
            java.util.regex.Matcher aut = Pattern.compile("@Autowired\\s+(?:private\\s+)?(\\w+Service)\\s+\\w+")
                    .matcher(testCode);
            if (aut.find()) className = aut.group(1);
        }
        if (className == null) return null;

        Path srcRoot = repoRoot.resolve("src/main/java");
        if (!Files.isDirectory(srcRoot)) return null;
        final String target = className + ".java";
        try (var stream = Files.walk(srcRoot)) {
            Path found = stream
                    .filter(p -> p.getFileName().toString().equals(target))
                    .findFirst()
                    .orElse(null);
            if (found == null) return null;
            String content = Files.readString(found);
            return content.length() > 8_000 ? content.substring(0, 8_000) : content;
        } catch (Exception e) {
            log.warn("[findSourceClassUnderTest] Failed to read {}: {}", target, e.getMessage());
            return null;
        }
    }

    private boolean isFrameworkPkg(String fqn) {
        return fqn.startsWith("java.") || fqn.startsWith("javax.")
                || fqn.startsWith("org.springframework.") || fqn.startsWith("org.testng.")
                || fqn.startsWith("org.mockito.") || fqn.startsWith("org.hibernate.")
                || fqn.startsWith("jakarta.") || fqn.startsWith("lombok.");
    }

    /** Adds the simple names of project classes (present in the index) referenced anywhere in text. */
    private void addIndexedRefs(String text, java.util.Map<String, Path> index, java.util.Set<String> out) {
        if (text == null) return;
        java.util.regex.Matcher m = Pattern.compile("\\b([A-Z][a-zA-Z0-9_]*)\\b").matcher(text);
        while (m.find()) {
            String n = m.group(1);
            if (index.containsKey(n)) out.add(n);
        }
    }

    /**
     * Collects the real source of every project class the corrector needs to fix a test, following
     * the dependency chain transitively (BFS, depth 2). Seeds come from the compile errors, the
     * classes the test instantiates, AND the class under test. The transitive walk is what surfaces
     * indirectly-needed entities — e.g. TestCaseService → ProjectAccessService → ProjectMemberRepository
     * → ProjectMember — so the corrector can build the missing DB setup (insert a ProjectMember to pass
     * checkMembership) instead of re-failing the same way each retry.
     */
    private String collectRelevantSources(String errors, String script, String sourceClass, Path repoRoot) {
        if (repoRoot == null) return null;
        Path srcRoot = repoRoot.resolve("src/main/java");
        if (!Files.isDirectory(srcRoot)) return null;

        // Index project classes: simpleName -> file (first match wins).
        java.util.Map<String, Path> index = new java.util.HashMap<>();
        try (var stream = Files.walk(srcRoot)) {
            stream.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                String fn = p.getFileName().toString();
                index.putIfAbsent(fn.substring(0, fn.length() - 5), p);
            });
        } catch (Exception e) {
            log.warn("[collectRelevantSources] Failed to index {}: {}", srcRoot, e.getMessage());
            return null;
        }
        if (index.isEmpty()) return null;

        // Seeds (depth 0).
        java.util.LinkedHashSet<String> seeds = new java.util.LinkedHashSet<>();
        if (errors != null) {
            java.util.regex.Matcher m = Pattern
                    .compile("\\b((?:[a-z][a-zA-Z0-9_]*\\.){2,}[A-Z][a-zA-Z0-9_]*)\\b").matcher(errors);
            while (m.find()) {
                String fqn = m.group(1);
                if (isFrameworkPkg(fqn)) continue;
                String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
                seeds.add(simple);
                if (simple.endsWith("Id") && simple.length() > 2) {
                    seeds.add(simple.substring(0, simple.length() - 2)); // ProjectMemberId -> ProjectMember
                }
            }
        }
        if (script != null) {
            java.util.regex.Matcher mn = Pattern.compile("\\bnew\\s+([A-Z][a-zA-Z0-9_]*)\\s*\\(").matcher(script);
            while (mn.find()) seeds.add(mn.group(1));
        }
        addIndexedRefs(sourceClass, index, seeds); // classes referenced by the service under test

        if (seeds.isEmpty()) return null;

        // BFS, depth 2 (3 levels), budget-capped.
        StringBuilder sb = new StringBuilder();
        java.util.Set<String> done = new java.util.HashSet<>();
        java.util.Map<String, Integer> depth = new java.util.HashMap<>();
        java.util.Deque<String> queue = new java.util.ArrayDeque<>();
        for (String s : seeds) { queue.add(s); depth.put(s, 0); }

        int budget = 18_000;
        while (!queue.isEmpty() && sb.length() < budget) {
            String name = queue.poll();
            if (!done.add(name)) continue;
            Path file = index.get(name);
            if (file == null) continue;
            String content;
            try {
                content = Files.readString(file);
            } catch (Exception e) {
                continue;
            }
            String capped = content.length() > 2_200 ? content.substring(0, 2_200) : content;
            if (sb.length() + capped.length() > budget) break;
            sb.append("// ===== ").append(name).append(" =====\n").append(capped).append("\n\n");

            int d = depth.getOrDefault(name, 0);
            if (d < 2) {
                java.util.LinkedHashSet<String> refs = new java.util.LinkedHashSet<>();
                addIndexedRefs(content, index, refs);
                for (String r : refs) {
                    if (!done.contains(r) && !depth.containsKey(r)) {
                        queue.add(r);
                        depth.put(r, d + 1);
                    }
                }
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * Reads the surefire report/dump files after a forked-process failure. A Spring context
     * startup failure ("Cannot instantiate class …") crashes the forked test JVM, so the real
     * "Caused by:" lives in target/surefire-reports/*.txt|*.dumpstream — not in the Maven console.
     */
    private String readSurefireFailureDetails(Path workDir) {
        if (workDir == null) return null;
        Path reports = workDir.resolve("target/surefire-reports");
        if (!Files.isDirectory(reports)) return null;
        StringBuilder sb = new StringBuilder();
        try (var stream = Files.list(reports)) {
            java.util.List<Path> files = stream
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".txt") || n.endsWith(".dumpstream") || n.endsWith(".dump");
                    })
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
            for (Path f : files) {
                try {
                    String content = Files.readString(f);
                    if (content.isBlank()) continue;
                    // The real root cause is in the "Caused by:" chain, often AFTER a huge verbose
                    // WebMergedContextConfiguration dump. Prefer the last "Caused by:" so we surface
                    // the actual bean/schema failure instead of the noisy config block.
                    int causeIdx = content.lastIndexOf("Caused by:");
                    String slice = (causeIdx >= 0)
                            ? content.substring(causeIdx, Math.min(content.length(), causeIdx + 3_500))
                            : (content.length() > 4_000 ? content.substring(0, 4_000) : content);
                    sb.append("----- ").append(f.getFileName()).append(" -----\n");
                    sb.append(slice).append("\n");
                    if (sb.length() > 8_000) break;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            return null;
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Returns [fqn, simpleName] of the project's @SpringBootApplication class, or null. */
    private String[] findSpringBootApplicationClass(Path repoRoot) {
        if (repoRoot == null) return null;
        Path srcRoot = repoRoot.resolve("src/main/java");
        if (!Files.isDirectory(srcRoot)) return null;
        java.util.List<Path> files;
        try (var stream = Files.walk(srcRoot)) {
            files = stream.filter(x -> x.toString().endsWith(".java"))
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            return null;
        }
        for (Path p : files) {
            try {
                String content = Files.readString(p);
                if (content.contains("@SpringBootApplication")) {
                    String fqn = srcRoot.relativize(p).toString()
                            .replace(java.io.File.separatorChar, '.')
                            .replaceAll("\\.java$", "");
                    String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
                    return new String[]{fqn, simple};
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Generated INTEGRATION tests live in package suites.integration, outside the app's package
     * tree, so a bare @SpringBootTest can't find the @SpringBootApplication by walking parent
     * packages ("Unable to find a @SpringBootConfiguration"). This deterministically injects
     * @SpringBootTest(classes = XxxApplication.class) + the import, regardless of what the LLM wrote.
     */
    private String ensureSpringBootTestConfig(String code, Path repoRoot) {
        if (code == null || !code.contains("@SpringBootTest")) return code;
        // Already declares config classes — leave it.
        if (Pattern.compile("@SpringBootTest\\s*\\([^)]*\\bclasses\\b").matcher(code).find()) return code;

        String[] app = findSpringBootApplicationClass(repoRoot);
        if (app == null) {
            log.warn("[ensureSpringBootTestConfig] No @SpringBootApplication class found; leaving test as-is");
            return code;
        }
        String fqn = app[0];
        String simple = app[1];

        if (Pattern.compile("@SpringBootTest\\s*\\(").matcher(code).find()) {
            code = code.replaceFirst("@SpringBootTest\\s*\\(", "@SpringBootTest(classes = " + simple + ".class, ");
        } else {
            code = code.replaceFirst("@SpringBootTest", "@SpringBootTest(classes = " + simple + ".class)");
        }

        if (!code.contains("import " + fqn + ";")) {
            int pkgEnd = code.indexOf(';');
            if (pkgEnd > 0 && code.substring(0, pkgEnd).contains("package")) {
                code = code.substring(0, pkgEnd + 1) + "\nimport " + fqn + ";\n" + code.substring(pkgEnd + 1);
            }
        }
        log.info("[ensureSpringBootTestConfig] Forced @SpringBootTest(classes = {}.class)", simple);
        return code;
    }

    /**
     * Removes the inline @TestPropertySource from a generated INTEGRATION test so the runner's
     * src/test/resources/application-test.properties governs the datasource instead. That file is
     * written per databaseType (Testcontainers PostgreSQL for POSTGRESQL), which — unlike the H2 the
     * LLM tends to hard-code — supports JSONB columns. The test keeps @ActiveProfiles("test").
     */
    private String stripInlineTestPropertySource(String code) {
        if (code == null || !code.contains("@TestPropertySource")) return code;
        String cleaned = code.replaceAll("@TestPropertySource\\s*\\([^)]*\\)\\s*", "");
        if (!cleaned.equals(code)) {
            log.info("[stripInlineTestPropertySource] Removed inline @TestPropertySource "
                    + "(datasource now driven by application-test.properties)");
        }
        return cleaned;
    }

    private String resolveBaseUrl(TestCase.TestType testType, Environment env) {
        if (env == null) return "";
        return switch (testType) {
            case WEB -> env.getBaseUrlWeb() != null ? env.getBaseUrlWeb() : "";
            case API, INTEGRATION -> env.getBaseUrlApi() != null ? env.getBaseUrlApi() : "";
            default -> "";
        };
    }

    private boolean isRetryableError(String logs) {
        if (logs == null) return false;
        // Spring context startup failures are config/infra, not the test's logic — we fix those
        // deterministically (classes=, application-test.properties). Re-prompting won't help.
        if (isContextLoadFailure(logs)) return false;
        return isCompilationError(logs) || isTestFailure(logs);
    }

    private boolean isContextLoadFailure(String logs) {
        if (logs == null) return false;
        return logs.contains("Failed to load ApplicationContext")
                || logs.contains("Cannot instantiate class")
                || logs.contains("Unable to find a @SpringBootConfiguration")
                || logs.contains("ApplicationContext failure threshold");
    }

    private boolean isCompilationError(String logs) {
        if (logs == null) return false;
        return logs.contains("COMPILATION ERROR")
                || logs.contains("cannot find symbol")
                || logs.contains("is not abstract and does not override")
                || logs.contains("incompatible types")
                || logs.contains("package does not exist")
                || logs.contains("cannot be applied to");
    }

    private boolean isTestFailure(String logs) {
        if (logs == null) return false;
        return logs.contains("Wanted but not invoked")
                || logs.contains("Actually, there were zero interactions")
                || logs.contains("AssertionError")
                || logs.contains("AssertionFailedError")
                || logs.contains("NullPointerException")
                || logs.contains("Unnecessary stubbings detected")
                || logs.contains("but was:")
                || (logs.contains("expected [") && logs.contains("] but found ["))
                // Generic surefire failure marker — a test ran and failed (assertion OR thrown
                // exception, e.g. a business RuntimeException from missing DB/moc setup). M3 can
                // often fix the setup. Context-load failures are excluded in isRetryableError.
                || logs.contains("<<< FAILURE!");
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
            case WEB -> "suites/herapp";
            case API -> "suites/api";
            case FUNCTIONAL_WEB -> "suites/functional/web";
            case FUNCTIONAL_MOBILE -> "suites/functional/mobile";
        };
    }

    private String extractUxSummary(String logs) {
        if (logs == null || logs.isBlank()) return null;
        String marker = "TEST_SUMMARY:";
        int idx = logs.indexOf(marker);
        if (idx < 0) return null;
        String after = logs.substring(idx + marker.length()).trim();
        // Take until next blank line or end
        int endIdx = after.indexOf("\n\n");
        if (endIdx > 0) return after.substring(0, endIdx).trim();
        int nl = after.indexOf('\n');
        if (nl > 0) return after.substring(0, nl).trim();
        return after.trim();
    }

    public String takeScreenshot(WebDriver driver, String testName) {
        if (driver == null) return null;
        if (!(driver instanceof TakesScreenshot takesScreenshot)) {
            return null;
        }

        try {
            byte[] bytes = takesScreenshot.getScreenshotAs(OutputType.BYTES);
            if (bytes == null || bytes.length == 0) return null;

            Path baseDir = CURRENT_EXECUTION_DIR.get();
            if (baseDir == null) {
                baseDir = Paths.get(System.getProperty("java.io.tmpdir"), "ms-execution");
            }
            Path screenshotsDir = baseDir.resolve("screenshots");
            Files.createDirectories(screenshotsDir);

            String safeName = (testName == null ? "test" : testName)
                    .replaceAll("[^a-zA-Z0-9._-]+", "_")
                    .replaceAll("_+", "_")
                    .replaceAll("^_+|_+$", "");
            if (safeName.isBlank()) safeName = "test";
            if (safeName.length() > 80) safeName = safeName.substring(0, 80);

            Path out = screenshotsDir.resolve(safeName + "-" + System.currentTimeMillis() + ".png");
            Files.write(out, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return out.toAbsolutePath().toString();
        } catch (Exception e) {
            log.debug("takeScreenshot failed: {}", e.toString());
            return null;
        }
    }

    private void attachLatestScreenshotIfPresent(ExecutionResult result, Path workDir, long testStartEpochMs) {
        if (result == null || workDir == null) return;
        if (result.getStatus() != ExecutionResult.ResultStatus.FAILURE && result.getStatus() != ExecutionResult.ResultStatus.ERROR) {
            return;
        }
        if (result.getScreenshotUrl() != null && !result.getScreenshotUrl().isBlank()) {
            return;
        }

        Path screenshotsDir = findScreenshotsDir(workDir);
        if (screenshotsDir == null) return;

        try (var stream = Files.list(screenshotsDir)) {
            Path latest = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName() != null ? p.getFileName().toString().toLowerCase() : "";
                        return name.endsWith(".png");
                    })
                    .filter(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis() >= (testStartEpochMs - 1_000);
                        } catch (Exception ignored) {
                            return true;
                        }
                    })
                    .max(Comparator.comparingLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (Exception ignored) {
                            return 0L;
                        }
                    }))
                    .orElse(null);

            if (latest != null) {
                result.setScreenshotUrl(latest.toAbsolutePath().toString());
                log.info("Attached screenshot to ExecutionResult: {}", latest);
            }
        } catch (Exception e) {
            log.debug("Unable to scan screenshots directory {}: {}", screenshotsDir, e.toString());
        }
    }

    private Path findScreenshotsDir(Path startDir) {
        Path current = startDir;
        while (current != null) {
            Path candidate = current.resolve("screenshots");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    /** Dernier screenshot PNG dans le dossier screenshots. */
    private Path findLatestScreenshot(Path workDir) {
        if (workDir == null) return null;
        Path screenshots = findScreenshotsDir(workDir);
        if (screenshots == null || !Files.isDirectory(screenshots)) return null;

        try (var stream = Files.list(screenshots)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName() != null && p.getFileName().toString().toLowerCase().endsWith(".png"))
                    .max(Comparator.comparingLong(p -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); } catch (Exception e) { return 0L; }
                    }))
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Error while searching for latest screenshot under {}: {}", screenshots, e.toString());
            return null;
        }
    }

    /** Copie du screenshot vers le répertoire permanent. */
    private void copyScreenshotToPermanent(Path src, ExecutionResult result) throws IOException {
        if (src == null || result == null) return;

        Path base = Paths.get(screenshotsDir == null ? "screenshots" : screenshotsDir);
        if (!base.isAbsolute()) {
            base = Paths.get(System.getProperty("user.dir")).resolve(base);
        }
        Files.createDirectories(base);

        String orig = src.getFileName() != null ? src.getFileName().toString() : ("screenshot-" + System.currentTimeMillis() + ".png");
        String prefix = (result.getTestCaseId() != null) ? ("tc-" + result.getTestCaseId() + "-") : "";
        String outName = prefix + System.currentTimeMillis() + "-" + orig;
        Path out = base.resolve(outName);

        Files.copy(src, out, StandardCopyOption.REPLACE_EXISTING);
        result.setScreenshotUrl(out.toAbsolutePath().toString());
        log.info("Copied screenshot {} -> {}", src, out);
    }

    /** Parse les rapports Surefire XML et retourne un tableau JSON des résultats par méthode. */
    private String parseSurefireReports(Path workDir) {
        if (workDir == null) return null;
        Path reportsDir = workDir.resolve("target/surefire-reports");
        if (!Files.isDirectory(reportsDir)) return null;

        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        try (var stream = Files.list(reportsDir)) {
            List<Path> xmlFiles = stream
                    .filter(p -> p.getFileName() != null
                            && p.getFileName().toString().startsWith("TEST-")
                            && p.getFileName().toString().endsWith(".xml"))
                    .collect(Collectors.toList());

            for (Path xmlFile : xmlFiles) {
                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(xmlFile.toFile());
                    NodeList testcases = doc.getElementsByTagName("testcase");

                    for (int i = 0; i < testcases.getLength(); i++) {
                        Element tc = (Element) testcases.item(i);
                        String methodName = tc.getAttribute("name");
                        String durationStr = tc.getAttribute("time");
                        long durationMs = 0;
                        try {
                            durationMs = Math.round(Double.parseDouble(durationStr) * 1000);
                        } catch (Exception ignored) {}

                        NodeList failures = tc.getElementsByTagName("failure");
                        NodeList errors   = tc.getElementsByTagName("error");

                        String status;
                        String message = null;
                        String stacktrace = null;

                        if (failures.getLength() > 0) {
                            status = "FAIL";
                            Element f = (Element) failures.item(0);
                            message    = f.getAttribute("message");
                            stacktrace = f.getTextContent().trim();
                        } else if (errors.getLength() > 0) {
                            status = "ERROR";
                            Element e = (Element) errors.item(0);
                            message    = e.getAttribute("message");
                            stacktrace = e.getTextContent().trim();
                        } else {
                            status = "PASS";
                        }

                        if (!first) json.append(",");
                        first = false;
                        json.append("{");
                        json.append("\"method\":").append(jsonStr(methodName)).append(",");
                        json.append("\"status\":").append(jsonStr(status)).append(",");
                        json.append("\"durationMs\":").append(durationMs);
                        if (message != null && !message.isBlank()) {
                            json.append(",\"message\":").append(jsonStr(message));
                        }
                        if (stacktrace != null && !stacktrace.isBlank()) {
                            // cap stacktrace at 1000 chars
                            String st = stacktrace.length() > 1000 ? stacktrace.substring(0, 1000) + "..." : stacktrace;
                            json.append(",\"stacktrace\":").append(jsonStr(st));
                        }
                        json.append("}");
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse Surefire XML {}: {}", xmlFile, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("Failed to list Surefire reports dir {}: {}", reportsDir, e.getMessage());
            return null;
        }

        json.append("]");
        return first ? null : json.toString(); // return null if no methods found
    }

    private String jsonStr(String value) {
        if (value == null) return "null";
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
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