package com.pfe.platform.ms_gestion.controller;

import com.pfe.platform.ms_gestion.dto.request.GenerateTestDataRequest;
import com.pfe.platform.ms_gestion.dto.request.GenerateTestRequest;
import com.pfe.platform.ms_gestion.dto.response.GenerateTestResponse;
import com.pfe.platform.ms_gestion.entity.Environment;
import com.pfe.platform.ms_gestion.entity.TestSuite;
import com.pfe.platform.ms_gestion.repository.EnvironmentRepository;
import com.pfe.platform.ms_gestion.repository.TestSuiteRepository;
import com.pfe.platform.ms_gestion.service.LlmService;
import com.pfe.platform.ms_gestion.service.TestDataGeneratorService;
import com.pfe.platform.ms_gestion.service.SkeletonExtractorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/llm")
public class LlmController {

    private final LlmService llmService;
    private final TestDataGeneratorService testDataGeneratorService;
    private final SkeletonExtractorService skeletonExtractorService;
    private final TestSuiteRepository testSuiteRepository;
    private final EnvironmentRepository environmentRepository;

    @PostMapping("/generate-test")
    public ResponseEntity<GenerateTestResponse> generateTest(@RequestBody GenerateTestRequest request) {
        String skeleton = null;

        // If the frontend already extracted the skeleton client-side, use it directly (no clone needed)
        if (request.getSkeleton() != null && !request.getSkeleton().isBlank()) {
            skeleton = request.getSkeleton();
        }

        // Otherwise fall back to backend extraction via GitHub clone
        // For UNIT/INTEGRATION with a targetClassName, extract skeleton from the source repo.
        // Priority: suite.gitRepoUrl > environment.gitRepoUrl (moved here in new workflow)
        String type = request.getType() != null ? request.getType().toUpperCase() : "";
        if (skeleton == null
                && ("UNIT".equals(type) || "INTEGRATION".equals(type))
                && request.getTargetClassName() != null && !request.getTargetClassName().isBlank()
                && request.getSuiteId() != null) {

            TestSuite suite = testSuiteRepository.findById(request.getSuiteId()).orElse(null);
            if (suite != null) {
                String gitRepoUrl = suite.getGitRepoUrl();
                String gitBranch  = suite.getGitBranch();

                // Fallback: use environment's gitRepoUrl (new workflow — repo is on env, not suite)
                if ((gitRepoUrl == null || gitRepoUrl.isBlank()) && suite.getProject() != null) {
                    Long projectId = suite.getProject().getId();
                    List<Environment> envs = environmentRepository.findByProjectId(projectId);
                    Environment firstWithRepo = envs.stream()
                            .filter(e -> e.getGitRepoUrl() != null && !e.getGitRepoUrl().isBlank())
                            .findFirst()
                            .orElse(null);
                    if (firstWithRepo != null) {
                        gitRepoUrl = firstWithRepo.getGitRepoUrl();
                        gitBranch  = firstWithRepo.getGitBranch();
                    }
                }

                if (gitRepoUrl != null && !gitRepoUrl.isBlank()) {
                    skeleton = skeletonExtractorService.extractSkeleton(
                            gitRepoUrl,
                            gitBranch,
                            suite.getModulePath(),
                            request.getTargetClassName().trim()
                    );
                }
            }
        }

        String code = llmService.generateTestCode(
                request.getType(),
                request.getDescription(),
                request.getDatabaseType(),
                skeleton,
                request.getTestData(),
                request.getMethodName(),
                request.getScenarioType(),
                request.getExpectedBehavior()
        );

        GenerateTestResponse resp = new GenerateTestResponse();
        resp.setGeneratedCode(code);
        return ResponseEntity.ok(resp);
    }

    /**
     * Generates realistic test data JSON for the given scenario.
     * Uses the LLM with structured field descriptions from Swagger.
     */
    @PostMapping("/generate-testdata")
    public ResponseEntity<Map<String, String>> generateTestData(
            @RequestBody GenerateTestDataRequest request) {
        String testData = testDataGeneratorService.generateTestData(request);
        return ResponseEntity.ok(Map.of("testData", testData));
    }
}
