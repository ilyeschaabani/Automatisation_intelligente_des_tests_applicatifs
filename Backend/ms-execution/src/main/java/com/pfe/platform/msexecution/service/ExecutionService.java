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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final CampaignRepository campaignRepository;
    private final CampaignTestCaseRepository campaignTestCaseRepository;
    private final ExecutionResultRepository executionResultRepository;
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
            repoDir = prepareRepository(project, campaign);
            log.info("Repository ready at: {}", repoDir);
            campaign.setProgress(20);
            campaign.setCurrentStep("Preparing campaign context");
            campaignRepository.save(campaign);
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

        // Map to cache suite repositories (clone once per suite)
        Map<Long, Path> suiteRepoDirs = new HashMap<>();

        boolean globalSuccess = true;
        int executedCount = 0;
        int totalTests = Math.max(ctcList.size(), 1);
        for (CampaignTestCase ctc : ctcList) {
            log.info("Processing test case ID: {}", ctc.getTestCaseId());
            TestCase tc = testCaseRepository.findById(ctc.getTestCaseId()).orElse(null);
            if (tc == null) {
                log.warn("Test case {} not found, skipping", ctc.getTestCaseId());
                continue;
            }

            // Determine working directory: suite repo if available, else project repo
            Path workDir = repoDir;
            if (tc.getSuite() != null && tc.getSuite().getGitRepoUrl() != null 
                && !tc.getSuite().getGitRepoUrl().isBlank()) {
                
                Long suiteId = tc.getSuite().getId();
                if (!suiteRepoDirs.containsKey(suiteId)) {
                    // Clone suite repository (once per suite)
                    String branch = tc.getSuite().getGitBranch() != null 
                            ? tc.getSuite().getGitBranch() 
                            : "main";
                    try {
                        Path clonedPath = cloneRepository(tc.getSuite().getGitRepoUrl(), branch);
                        suiteRepoDirs.put(suiteId, clonedPath);
                        log.info("Cloned suite repository ID {} from {} at {}", 
                                suiteId, tc.getSuite().getGitRepoUrl(), clonedPath);
                    } catch (Exception e) {
                        log.error("Failed to clone suite {} repository, falling back to project repo", 
                                suiteId, e);
                        // Fallback to project repository
                    }
                }
                workDir = suiteRepoDirs.getOrDefault(suiteId, repoDir);
            }

            log.info("Executing test: {} ({})", tc.getId(),
                    tc.getGenerated() ? "GENERATED-" + tc.getId() : tc.getScriptPath());
            ExecutionResult result = executeRealTest(tc, env, workDir);
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

        // Clean up cloned suite repositories
        suiteRepoDirs.values().forEach(this::deleteDirectory);
        log.info("Cleaned up {} cloned suite repositories", suiteRepoDirs.size());

        // Clean up project repository
        if (repoDir != null) {
            deleteDirectory(repoDir);
            log.info("Cleaned up project repository at: {}", repoDir);
        }

        log.info("Execution complete. Executed: {}, Global success: {}", executedCount, globalSuccess);
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
            log.info("Using built-in AI test template (no Git URL)");
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
        Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(dir.toFile())
                .setBranch(branch)
                .call();
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

    private ExecutionResult executeRealTest(TestCase tc, Environment env, Path repoDir) {
        long start = System.currentTimeMillis();
        ExecutionResult result = new ExecutionResult();
        result.setStatus(ExecutionResult.ResultStatus.SUCCESS);
        result.setTestType(tc.getType());

        try {
            String className;
            // ----- TEST GÉNÉRÉ PAR IA -----
            if (Boolean.TRUE.equals(tc.getGenerated()) && tc.getGeneratedCode() != null) {
                String cleanCode = tc.getGeneratedCode()
                        .replaceAll("(?i)```java\\s*", "")
                        .replaceAll("```", "")
                        .trim();

                String subDir = getTestPackageByType(tc.getType());
                Path genDir = repoDir.resolve("src/test/java").resolve(subDir);
                Files.createDirectories(genDir);

                String shortClassName = "Generated_" + tc.getId();
                Path testFile = genDir.resolve(shortClassName + ".java");

                String finalCode = cleanCode.replaceFirst(
                        "\\bclass\\s+\\w+",
                        "class " + shortClassName
                );

                if (!finalCode.contains("package " + subDir.replace('/', '.'))) {
                    finalCode = "package " + subDir.replace('/', '.') + ";\n\n" + finalCode;
                }

                Files.writeString(testFile, finalCode);
                log.info("Generated test written to: {}", testFile);

                className = subDir.replace('/', '.') + "." + shortClassName;
            } else {
                className = tc.getScriptPath()
                        .replace("/", ".")
                        .replace(".java", "");
            }
            log.debug("Final class name for Maven: {}", className);

            List<String> command = buildMavenCommand(tc, env, repoDir, className);

            log.info("Maven command: {}", String.join(" ", command));
            log.info("Working directory: {}", repoDir);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(repoDir.toFile());
            pb.redirectErrorStream(true);

            long processStartTime = System.currentTimeMillis();
            Process process = pb.start();

            boolean finished = process.waitFor(mavenTimeoutMinutes, TimeUnit.MINUTES);
            long waitForCompletedTime = System.currentTimeMillis();
            log.info("[DIAGNOSTIC] process.waitFor() completed at: {} (elapsed: {}ms, finished={})",
                    waitForCompletedTime, (waitForCompletedTime - processStartTime), finished);

            String output = new String(process.getInputStream().readAllBytes());
            long outputReadTime = System.currentTimeMillis();
            log.info("[DIAGNOSTIC] Output read completed at: {} (elapsed: {}ms)",
                    outputReadTime, (outputReadTime - waitForCompletedTime));

            if (!finished) {
                process.destroyForcibly();
                result.setDurationMs(System.currentTimeMillis() - start);
                result.setStatus(ExecutionResult.ResultStatus.ERROR);
                result.setErrorMessage("Maven timeout after " + mavenTimeoutMinutes + " minutes");
                result.setLogs(output);
                return result;
            }

            int exitCode = process.exitValue();
            long duration = System.currentTimeMillis() - start;
            result.setDurationMs(duration);
            result.setLogs(output);

            if (exitCode != 0) {
                result.setStatus(ExecutionResult.ResultStatus.FAILURE);
                result.setErrorMessage("Maven exit code : " + exitCode);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            result.setDurationMs(duration);
            result.setStatus(ExecutionResult.ResultStatus.ERROR);
            result.setErrorMessage(e.getClass().getSimpleName() + " : " + e.getMessage());
            log.error("EXCEPTION during test execution", e);
        }

        return result;
    }

    private List<String> buildMavenCommand(TestCase tc, Environment env, Path repoDir, String className) {
        List<String> command = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            command.add("cmd");
            command.add("/c");
            command.add(repoDir.resolve("mvnw.cmd").toString());
        } else {
            command.add(repoDir.resolve("mvnw").toString());
        }

        command.add("test");
        command.add("-Dtest=" + className);

        TestCase.TestType testType = tc.getType() != null ? tc.getType() : TestCase.TestType.WEB;
        String baseUrl = resolveBaseUrl(testType, env);

        switch (testType) {
            case UNIT:
                command.add("-Dtest.layer=unit");
                break;
            case INTEGRATION:
                command.add("-Dtest.layer=integration");
                command.add("-DBASE_URL=" + baseUrl);
                break;
            case API:
            case WEB:
            default:
                command.add("-Dtest.layer=e2e");
                command.add("-DBASE_URL=" + baseUrl);
                break;
        }

        if (env != null && env.getVariables() != null && !env.getVariables().isEmpty()) {
            command.add("-Denv.variables=" + env.getVariables());
        }

        return command;
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
                Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.delete(p); } catch (IOException ignored) {}
                        });
            }
        } catch (IOException ignored) {}
    }

    private String getTestPackageByType(TestCase.TestType type) {
        if (type == null) return "suites/generated";
        return switch (type) {
            case UNIT -> "suites/unit";
            case INTEGRATION -> "suites/integration";
            case WEB, API -> "suites/herapp";
        };
    }
}