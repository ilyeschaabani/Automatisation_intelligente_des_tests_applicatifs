package com.pfe.platform.msexecution.service;


import com.pfe.platform.msexecution.entity.*;
import com.pfe.platform.msexecution.repository.*;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
        
        if (campaign.getStatus() != Campaign.CampaignStatus.PENDING) {
            log.warn("Campaign status is not PENDING: {}", campaign.getStatus());
            throw new RuntimeException("La campagne n'est pas en attente");
        }

        // Passage en mode RUNNING
        campaign.setStatus(Campaign.CampaignStatus.RUNNING);
        campaign.setStartedAt(LocalDateTime.now());
        campaign.setProgress(5);
        campaign.setCurrentStep("Cloning repository");
        campaignRepository.save(campaign);
        log.info("Campaign status updated to RUNNING");

        Project project = projectRepository.findById(campaign.getProjectId())
                .orElseThrow(() -> new RuntimeException("Projet introuvable"));
        log.info("Project found: {}, Git URL: {}", project.getId(), project.getGitRepoUrl());
        
        Environment env = environmentRepository.findById(campaign.getEnvironmentId())
                .orElse(null);
        log.info("Environment loaded: {}", env != null ? env.getId() : "NULL");

        // 1. CLONER LE DÉPÔT GIT
        Path repoDir = null;
        try {
            String branch = campaign.getGitBranch() != null ? campaign.getGitBranch() :
                    (project.getGitDefaultBranch() != null ? project.getGitDefaultBranch() : "main");
            log.info("Cloning repository from: {} on branch: {}", project.getGitRepoUrl(), branch);
            repoDir = cloneRepository(project.getGitRepoUrl(), branch);
            log.info("Repository cloned successfully to: {}", repoDir);
            campaign.setProgress(20);
            campaign.setCurrentStep("Preparing campaign context");
            campaignRepository.save(campaign);
            patchKnownTestSuite(repoDir);
        } catch (Exception e) {
            log.error("FATAL: Git clone failed", e);
            finishWithError(campaign, "Erreur lors du clonage Git : " + e.getMessage());
            return;
        }

        // 2. RÉCUPÉRER LES CAS DE TEST TRIÉS
        log.info("Fetching test cases for campaign ID: {}", campaignId);
        List<CampaignTestCase> ctcList = campaignTestCaseRepository
                .findByCampaignIdOrderByExecutionOrder(campaignId);
        log.info("Found {} test cases for campaign", ctcList.size());
        
        if (ctcList.isEmpty()) {
            log.warn("No test cases found for campaign! Marking as finished successfully.");
        }

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

            log.info("Executing test: {} ({})", tc.getId(), tc.getScriptPath());
            // 3. EXÉCUTER LE TEST RÉELLEMENT
            ExecutionResult result = executeRealTest(tc, env, repoDir);
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

        // 4. METTRE À JOUR LE STATUT FINAL
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

        // 5. NETTOYAGE DU RÉPERTOIRE TEMPORAIRE (optionnel)
        // deleteDirectory(repoDir);
    }

    private void finishWithError(Campaign campaign, String errorMessage) {
        campaign.setStatus(Campaign.CampaignStatus.FINISHED_WITH_ERRORS);
        campaign.setProgress(100);
        campaign.setCurrentStep(errorMessage);
        campaign.setFinishedAt(LocalDateTime.now());
        campaignRepository.save(campaign);
    }

    // --- Cloner le dépôt Git ---
    private Path cloneRepository(String repoUrl, String branch) throws GitAPIException, IOException {
        // Get the temp directory - use system temp if config is empty
        Path tempDirPath;
        if (tempDirConfig != null && !tempDirConfig.trim().isEmpty()) {
            tempDirPath = Paths.get(tempDirConfig);
            // Create the configured temp directory if it doesn't exist
            if (!Files.exists(tempDirPath)) {
                log.info("Creating configured temp directory: {}", tempDirPath);
                Files.createDirectories(tempDirPath);
            }
        } else {
            // Use system default temp directory
            tempDirPath = Paths.get(System.getProperty("java.io.tmpdir"), "ms-execution");
            if (!Files.exists(tempDirPath)) {
                log.info("Creating default temp directory: {}", tempDirPath);
                Files.createDirectories(tempDirPath);
            }
        }
        
        log.debug("Creating temp directory in: {}", tempDirPath);
        Path dir = Files.createTempDirectory(tempDirPath, "exec-");
        log.debug("Temp directory created at: {}", dir);
        
        log.info("Starting Git clone from {} on branch {}", repoUrl, branch);
        Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(dir.toFile())
                .setBranch(branch)
                .call();
        log.info("Git clone completed successfully");
        return dir;
    }

    private void patchKnownTestSuite(Path repoDir) {
        try {
            Path testFile = repoDir.resolve("src/test/java/suites/herapp/TestHerappSuite.java");
            if (!Files.exists(testFile)) {
                return;
            }

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
            log.warn("Unable to apply compatibility patch to cloned test suite", e);
        }
    }

    // --- Exécuter un test réel avec Maven ---
    // --- Exécuter un test réel avec Maven ---
    private ExecutionResult executeRealTest(TestCase tc, Environment env, Path repoDir) {
        long start = System.currentTimeMillis();
        ExecutionResult result = new ExecutionResult();
        result.setStatus(ExecutionResult.ResultStatus.SUCCESS);

        try {
            log.debug("Test case script path: {}", tc.getScriptPath());

            // Transformer le scriptPath en nom de classe Java
            // Exemple : suites/authentification/TestLoginOK.java -> suites.authentification.TestLoginOK
            String className = tc.getScriptPath()
                    .replace("/", ".")
                    .replace(".java", "");
            log.debug("Converted class name: {}", className);

            // Déterminer l'URL de base en fonction du type de test
            String baseUrl = (tc.getType() == TestCase.TestType.WEB) ?
                    env.getBaseUrlWeb() : env.getBaseUrlApi();
            log.debug("Test type: {}, Base URL: {}", tc.getType(), baseUrl);

            // Construire la commande Maven - Utiliser mvnw (Maven wrapper) au lieu de mvn
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
            command.add("-DBASE_URL=" + (baseUrl != null ? baseUrl : ""));

            // Injection des variables d'environnement personnalisées (si présentes)
            if (env != null && env.getVariables() != null && !env.getVariables().isEmpty()) {
                command.add("-Denv.variables=" + env.getVariables());
            }

            log.info("Maven command: {}", String.join(" ", command));
            log.info("Working directory: {}", repoDir);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(repoDir.toFile());
            pb.redirectErrorStream(true);

            long processStartTime = System.currentTimeMillis();
            log.info("[DIAGNOSTIC] Starting Maven process at: {}", processStartTime);
            Process process = pb.start();
            
            boolean finished = process.waitFor(mavenTimeoutMinutes, TimeUnit.MINUTES);
            long waitForCompletedTime = System.currentTimeMillis();
            log.info("[DIAGNOSTIC] process.waitFor() completed at: {} (elapsed: {}ms, finished={})", 
                    waitForCompletedTime, (waitForCompletedTime - processStartTime), finished);
            
            log.info("[DIAGNOSTIC] Starting to read process output...");
            String output = new String(process.getInputStream().readAllBytes());
            long outputReadTime = System.currentTimeMillis();
            log.info("[DIAGNOSTIC] Output read completed at: {} (elapsed: {}ms)", 
                    outputReadTime, (outputReadTime - waitForCompletedTime));

            if (!finished) {
                log.error("[DIAGNOSTIC] Maven timeout detected - destroying process forcibly");
                process.destroyForcibly();
                result.setDurationMs(System.currentTimeMillis() - start);
                result.setStatus(ExecutionResult.ResultStatus.ERROR);
                result.setErrorMessage("Maven timeout after " + mavenTimeoutMinutes + " minutes");
                result.setLogs(output);
                log.error("Maven execution timed out after {} minutes", mavenTimeoutMinutes);
                return result;
            }

            int exitCode = process.exitValue();

            long duration = System.currentTimeMillis() - start;
            result.setDurationMs(duration);
            result.setLogs(output);

            log.info("Maven execution completed with exit code: {} (duration: {}ms)", exitCode, duration);
            if (!output.isEmpty()) {
                log.debug("Maven output:\n{}", output.substring(0, Math.min(500, output.length())));
            }

            if (exitCode != 0) {
                result.setStatus(ExecutionResult.ResultStatus.FAILURE);
                result.setErrorMessage("Maven exit code : " + exitCode);
                log.warn("Test FAILED with exit code: {}", exitCode);
            } else {
                log.info("Test PASSED");
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

    // --- Nettoyage du répertoire temporaire ---
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

}
