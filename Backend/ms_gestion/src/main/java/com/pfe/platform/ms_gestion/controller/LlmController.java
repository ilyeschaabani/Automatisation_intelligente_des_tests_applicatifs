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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/llm")
@PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')")
public class LlmController {

    private final LlmService llmService;
    private final TestDataGeneratorService testDataGeneratorService;
    private final SkeletonExtractorService skeletonExtractorService;
    private final TestSuiteRepository testSuiteRepository;
    private final EnvironmentRepository environmentRepository;

    @PostMapping("/generate-test")
    public ResponseEntity<GenerateTestResponse> generateTest(@RequestBody GenerateTestRequest request) {
        log.info("=== GENERATE-TEST REQUEST ===");
        log.info("type={}, targetClass={}, suiteId={}, methodName={}, scenarioType={}",
                request.getType(), request.getTargetClassName(), request.getSuiteId(),
                request.getMethodName(), request.getScenarioType());
        log.info("description={}", request.getDescription());
        log.info("skeleton present={}, testData present={}, expectedBehavior={}",
                request.getSkeleton() != null && !request.getSkeleton().isBlank(),
                request.getTestData() != null && !request.getTestData().isBlank(),
                request.getExpectedBehavior());

        String skeleton = null;
        String fileTree = null;
        String dependencySources = null;

        if (request.getSkeleton() != null && !request.getSkeleton().isBlank()) {
            skeleton = request.getSkeleton();
            log.info("Using skeleton from request ({} chars)", skeleton.length());
        }

        String type = request.getType() != null ? request.getType().toUpperCase() : "";
        if (("UNIT".equals(type) || "INTEGRATION".equals(type))
                && request.getTargetClassName() != null && !request.getTargetClassName().isBlank()
                && request.getSuiteId() != null) {

            TestSuite suite = testSuiteRepository.findById(request.getSuiteId()).orElse(null);
            if (suite == null) {
                log.warn("Suite not found for id={}", request.getSuiteId());
            } else {
                log.info("Suite found: id={}, name={}, modulePath={}", suite.getId(), suite.getName(), suite.getModulePath());
                String gitRepoUrl = suite.getGitRepoUrl();
                String gitBranch  = suite.getGitBranch();
                log.info("Suite gitRepoUrl={}, gitBranch={}", gitRepoUrl, gitBranch);

                if ((gitRepoUrl == null || gitRepoUrl.isBlank()) && suite.getProject() != null) {
                    Long projectId = suite.getProject().getId();
                    log.info("Suite has no gitRepoUrl, looking in environments for projectId={}", projectId);
                    List<Environment> envs = environmentRepository.findByProjectId(projectId);
                    log.info("Found {} environments for project", envs.size());
                    Environment firstWithRepo = envs.stream()
                            .filter(e -> e.getGitRepoUrl() != null && !e.getGitRepoUrl().isBlank())
                            .findFirst()
                            .orElse(null);
                    if (firstWithRepo != null) {
                        gitRepoUrl = firstWithRepo.getGitRepoUrl();
                        gitBranch  = firstWithRepo.getGitBranch();
                        log.info("Using env gitRepoUrl={}, gitBranch={}", gitRepoUrl, gitBranch);
                    } else {
                        log.warn("No environment with gitRepoUrl found for project");
                    }
                }

                if (gitRepoUrl != null && !gitRepoUrl.isBlank()) {
                    log.info("Starting source extraction for class={} from repo", request.getTargetClassName());
                    var extraction = skeletonExtractorService.extractSkeletonWithTree(
                            gitRepoUrl,
                            gitBranch,
                            suite.getModulePath(),
                            request.getTargetClassName().trim()
                    );
                    if (extraction != null) {
                        if (extraction.skeleton() != null && !extraction.skeleton().isBlank()) {
                            log.info("Overriding frontend skeleton ({} chars) with full extracted source ({} chars)",
                                    skeleton != null ? skeleton.length() : 0, extraction.skeleton().length());
                            skeleton = extraction.skeleton();
                        }
                        fileTree = extraction.fileTree();
                        dependencySources = extraction.dependencySources();
                        log.info("Extraction OK: skeleton={} chars, fileTree={} chars, deps={} chars",
                                skeleton != null ? skeleton.length() : 0,
                                fileTree != null ? fileTree.length() : 0,
                                dependencySources != null ? dependencySources.length() : 0);
                    } else {
                        log.warn("Extraction returned null");
                    }
                } else {
                    log.warn("No gitRepoUrl available — skipping source extraction");
                }
            }
        } else {
            log.info("Skipping extraction: type={}, targetClass={}, suiteId={}", type, request.getTargetClassName(), request.getSuiteId());
        }

        log.info("Calling LLM for test generation...");
        String code = llmService.generateTestCode(
                request.getType(),
                request.getDescription(),
                request.getDatabaseType(),
                skeleton,
                request.getTestData(),
                request.getMethodName(),
                request.getScenarioType(),
                request.getExpectedBehavior(),
                fileTree,
                dependencySources
        );
        log.info("=== GENERATE-TEST DONE === code={} chars", code != null ? code.length() : 0);

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
        log.info("=== GENERATE-TESTDATA REQUEST ===");
        log.info("methodName={}, scenarioType={}, targetClassName={}",
                request.getMethodName(), request.getScenarioType(), request.getTargetClassName());
        log.info("fields count={}, skeleton present={}",
                request.getFields() != null ? request.getFields().size() : 0,
                request.getSkeleton() != null && !request.getSkeleton().isBlank());
        if (request.getFields() != null) {
            request.getFields().forEach(f ->
                log.info("  field: name={}, type={}, required={}, enum={}", f.getName(), f.getType(), f.isRequired(), f.getEnumValues())
            );
        }
        String testData = testDataGeneratorService.generateTestData(request);
        log.info("=== GENERATE-TESTDATA DONE === result={}", testData);
        return ResponseEntity.ok(Map.of("testData", testData));
    }

    @PostMapping("/extract-dto-fields")
    public ResponseEntity<?> extractDtoFields(@RequestBody Map<String, Object> request) {
        Long suiteId = request.get("suiteId") != null ? Long.valueOf(request.get("suiteId").toString()) : null;
        String className = (String) request.get("className");
        log.info("=== EXTRACT-DTO-FIELDS === suiteId={}, className={}", suiteId, className);

        if (suiteId == null || className == null || className.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "suiteId and className are required"));
        }

        TestSuite suite = testSuiteRepository.findById(suiteId).orElse(null);
        if (suite == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Suite not found"));
        }

        String gitRepoUrl = suite.getGitRepoUrl();
        String gitBranch = suite.getGitBranch();
        if ((gitRepoUrl == null || gitRepoUrl.isBlank()) && suite.getProject() != null) {
            List<Environment> envs = environmentRepository.findByProjectId(suite.getProject().getId());
            Environment env = envs.stream()
                    .filter(e -> e.getGitRepoUrl() != null && !e.getGitRepoUrl().isBlank())
                    .findFirst().orElse(null);
            if (env != null) {
                gitRepoUrl = env.getGitRepoUrl();
                gitBranch = env.getGitBranch();
            }
        }

        if (gitRepoUrl == null || gitRepoUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No git repo URL found"));
        }

        var fields = skeletonExtractorService.extractDtoFields(gitRepoUrl, gitBranch, suite.getModulePath(), className);
        log.info("=== EXTRACT-DTO-FIELDS DONE === {} fields extracted", fields.size());

        var result = fields.stream().map(f -> Map.of(
                "name", (Object) f.name(),
                "type", f.type(),
                "required", f.required(),
                "enumValues", f.enumValues()
        )).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("fields", result));
    }
}
