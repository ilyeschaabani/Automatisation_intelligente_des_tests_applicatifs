package com.pfe.platform.ms_gestion.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.platform.ms_gestion.dto.request.CreateTestCaseRequest;
import com.pfe.platform.ms_gestion.dto.response.TestCaseResponse;
import com.pfe.platform.ms_gestion.entity.Project;
import com.pfe.platform.ms_gestion.entity.ProjectMember;
import com.pfe.platform.ms_gestion.entity.TestCase;
import com.pfe.platform.ms_gestion.entity.TestSuite;
import com.pfe.platform.ms_gestion.repository.ProjectMemberRepository;
import com.pfe.platform.ms_gestion.repository.TestCaseRepository;
import com.pfe.platform.ms_gestion.repository.TestSuiteRepository;
import com.pfe.platform.ms_gestion.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TestCaseService {
    private final TestCaseRepository testCaseRepository;
    private final TestSuiteRepository testSuiteRepository;
    private final ProjectMemberRepository projectMemberRepository;

    private final LlmService llmService;
    private final SkeletonExtractorService skeletonExtractorService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public TestCaseResponse add(Long suiteId, CreateTestCaseRequest request) {
        TestSuite suite = getSuiteOrThrow(suiteId);
        checkProjectRole(suite.getProject(), ProjectMember.Role.ADMIN, ProjectMember.Role.TESTER);

        TestCase tc = new TestCase();
        tc.setSuite(suite);
        tc.setTitle(request.getTitle());
        tc.setDescription(request.getDescription());
        tc.setType(TestCase.TestType.valueOf(request.getType().toUpperCase()));
        tc.setSpringProfile(request.getSpringProfile());
        tc.setPriority(request.getPriority());
        tc.setRiskLevel(TestCase.RiskLevel.valueOf(request.getRiskLevel().toUpperCase()));
        tc.setGitRepoUrl(request.getGitRepoUrl());
        tc.setTestData(ensureValidJson(request.getTestData()));
        tc.setTags(request.getTags());
        tc.setMaxDurationSeconds(request.getMaxDurationSeconds());
        tc.setDatabaseType(request.getDatabaseType());
        tc.setTargetClassName(request.getTargetClassName());

        // -------------------------------------------------------
        // Gestion du mode IA / manuel
        // Règle métier: pour les suites UNIT/INTEGRATION, on force toujours la génération IA.
        // Sinon:
        // - si le projet parent est en mode IA (pas de gitRepoUrl), on force la génération IA
        // - sinon, conserver le comportement (useAI / generatedCode / scriptPath)
        // -------------------------------------------------------
        boolean suiteForcesAi = suite.getType() == TestSuite.TestType.UNIT || suite.getType() == TestSuite.TestType.INTEGRATION;

        // databaseType is now inherited from Environment — validation removed at TestCase level

        if (suiteForcesAi) {
            // If the user provides edited/generated code, persist it as-is.
            if (request.getGeneratedCode() != null && !request.getGeneratedCode().trim().isEmpty()) {
                tc.setGeneratedCode(request.getGeneratedCode());
                tc.setGenerated(true);
                tc.setScriptPath(null);
            } else {
                // Otherwise, require a prompt and generate via LLM.
                if (request.getDescriptionAI() == null || request.getDescriptionAI().isBlank()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "descriptionAI is required when suite type is " + suite.getType()
                    );
                }
                String skeleton = resolveSkeletonForSuite(suite, request.getTargetClassName());
                String generatedCode = llmService.generateTestCode(
                        suite.getType().name(),
                    request.getDescriptionAI().trim(),
                    request.getDatabaseType(),
                    skeleton
                );
                tc.setGeneratedCode(generatedCode);
                tc.setGenerated(true);
                tc.setScriptPath(null);
            }
        } else {
            boolean projectIsAi = false;
            if (suite.getProject() != null) {
                String repo = suite.getProject().getGitRepoUrl();
                projectIsAi = (repo == null || repo.isBlank());
            }

            boolean hasRepo = request.getGitRepoUrl() != null && !request.getGitRepoUrl().isBlank();
            boolean hasScriptPath = request.getScriptPath() != null && !request.getScriptPath().isBlank();

            if (hasRepo || hasScriptPath) {
                if (!hasRepo || !hasScriptPath) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gitRepoUrl and scriptPath are required for manual/import mode");
                }
                tc.setGitRepoUrl(request.getGitRepoUrl());
                tc.setScriptPath(request.getScriptPath());
                tc.setGenerated(false);
                tc.setGeneratedCode(null);
            } else if (projectIsAi) {
                // Force generation from LLM using descriptionAI if provided, otherwise use description
                String promptDesc = request.getDescriptionAI() != null && !request.getDescriptionAI().isBlank()
                        ? request.getDescriptionAI()
                        : request.getDescription();
                String generatedCode = llmService.generateTestCode(
                        request.getType(),
                        promptDesc,
                        request.getDatabaseType()
                );
                tc.setGeneratedCode(generatedCode);
                tc.setGenerated(true);
                tc.setScriptPath(null);
                tc.setGitRepoUrl(null);
            } else {
                if (Boolean.TRUE.equals(request.getUseAI()) && request.getDescriptionAI() != null) {
                    // Cas 1 : régénérer depuis l'IA
                    String generatedCode = llmService.generateTestCode(
                            request.getType(),
                            request.getDescriptionAI(),
                            request.getDatabaseType()
                    );
                    tc.setGeneratedCode(generatedCode);
                    tc.setGenerated(true);
                    tc.setScriptPath(null);
                    tc.setGitRepoUrl(null);
                } else if (request.getGeneratedCode() != null && !request.getGeneratedCode().trim().isEmpty()) {
                    // Cas 2 : code généré/édité par l'utilisateur
                    tc.setGeneratedCode(request.getGeneratedCode());
                    tc.setGenerated(true);
                    tc.setScriptPath(null);
                    tc.setGitRepoUrl(null);
                } else {
                    // Cas 3 : mode manuel
                    if (request.getScriptPath() == null || request.getScriptPath().isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scriptPath is required for manual mode");
                    }
                    tc.setScriptPath(request.getScriptPath());
                    tc.setGenerated(false);
                }
            }
        }

        tc = testCaseRepository.save(tc);
        return mapToResponse(tc);
    }

    public List<TestCaseResponse> list(Long suiteId) {
        TestSuite suite = getSuiteOrThrow(suiteId);
        checkMembership(suite.getProject());
        return testCaseRepository.findBySuiteId(suiteId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public TestCaseResponse get(Long suiteId, Long caseId) {
        TestCase tc = getTestCaseOrThrow(caseId, suiteId);
        checkMembership(tc.getSuite().getProject());
        return mapToResponse(tc);
    }

    @Transactional
    public TestCaseResponse update(Long suiteId, Long caseId, CreateTestCaseRequest request) {
        TestCase tc = getTestCaseOrThrow(caseId, suiteId);
        checkProjectRole(tc.getSuite().getProject(), ProjectMember.Role.ADMIN, ProjectMember.Role.TESTER);

        boolean suiteForcesAi = tc.getSuite().getType() == TestSuite.TestType.UNIT
            || tc.getSuite().getType() == TestSuite.TestType.INTEGRATION;

        tc.setTitle(request.getTitle());
        tc.setDescription(request.getDescription());
        tc.setType(TestCase.TestType.valueOf(request.getType().toUpperCase()));
        tc.setSpringProfile(request.getSpringProfile());
        tc.setPriority(request.getPriority());
        tc.setRiskLevel(TestCase.RiskLevel.valueOf(request.getRiskLevel().toUpperCase()));
        tc.setGitRepoUrl(request.getGitRepoUrl());
        tc.setTestData(ensureValidJson(request.getTestData()));
        tc.setTags(request.getTags());
        tc.setMaxDurationSeconds(request.getMaxDurationSeconds());
        tc.setDatabaseType(request.getDatabaseType());

        boolean hasRepo = request.getGitRepoUrl() != null && !request.getGitRepoUrl().isBlank();
        boolean hasScriptPath = request.getScriptPath() != null && !request.getScriptPath().isBlank();

        // Handle code mode updates.
        // Priority:
        // 1) If repo + scriptPath => manual/import mode
        // 2) If generatedCode provided => persist it (user edited)
        // 3) Else if useAI+descriptionAI => regenerate
        // 4) Else if scriptPath provided => manual mode
        // 5) Else => keep existing code/mode (metadata-only update)
        if (hasRepo || hasScriptPath) {
            if (!hasRepo || !hasScriptPath) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gitRepoUrl and scriptPath are required for manual/import mode");
            }
            tc.setGitRepoUrl(request.getGitRepoUrl());
            tc.setScriptPath(request.getScriptPath());
            tc.setGenerated(false);
            tc.setGeneratedCode(null);
        } else if (request.getGeneratedCode() != null && !request.getGeneratedCode().trim().isEmpty()) {
            tc.setGeneratedCode(request.getGeneratedCode());
            tc.setGenerated(true);
            tc.setScriptPath(null);
            tc.setGitRepoUrl(null);
        } else if (Boolean.TRUE.equals(request.getUseAI())
                && request.getDescriptionAI() != null
                && !request.getDescriptionAI().isBlank()) {
            String generatedCode = llmService.generateTestCode(
                    request.getType(),
                    request.getDescriptionAI(),
                    request.getDatabaseType()
            );
            tc.setGeneratedCode(generatedCode);
            tc.setGenerated(true);
            tc.setScriptPath(null);
            tc.setGitRepoUrl(null);
        } else if (request.getScriptPath() != null && !request.getScriptPath().isBlank()) {
            tc.setScriptPath(request.getScriptPath());
            tc.setGenerated(false);
            tc.setGeneratedCode(null);
        } else {
            // keep existing tc.generated/tc.generatedCode/tc.scriptPath
        }

        if (suiteForcesAi) {
            // UNIT/INTEGRATION suites must always remain in generated mode.
            tc.setScriptPath(null);
            if (!Boolean.TRUE.equals(tc.getGenerated()) || tc.getGeneratedCode() == null || tc.getGeneratedCode().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "generatedCode or descriptionAI is required when suite type is " + tc.getSuite().getType()
                );
            }
        }

        return mapToResponse(testCaseRepository.save(tc));
    }

    @Transactional
    public void delete(Long suiteId, Long caseId) {
        TestCase tc = getTestCaseOrThrow(caseId, suiteId);
        checkProjectRole(tc.getSuite().getProject(), ProjectMember.Role.ADMIN);
        testCaseRepository.delete(tc);
    }

    private TestSuite getSuiteOrThrow(Long suiteId) {
        return testSuiteRepository.findById(suiteId)
                .orElseThrow(() -> new RuntimeException("Suite non trouvée"));
    }

    private TestCase getTestCaseOrThrow(Long caseId, Long suiteId) {
        TestCase tc = testCaseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Cas de test non trouvé"));
        if (!tc.getSuite().getId().equals(suiteId)) {
            throw new RuntimeException("Le cas de test n'appartient pas à cette suite");
        }
        return tc;
    }

    private void checkMembership(Project project) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!projectMemberRepository.existsByProjectIdAndUserId(project.getId(), userId)) {
            throw new RuntimeException("Vous n'êtes pas membre de ce projet");
        }
    }

    private void checkProjectRole(Project project, ProjectMember.Role... allowedRoles) {
        Long userId = SecurityUtils.getCurrentUserId();
        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserId(project.getId(), userId)
                .orElseThrow(() -> new RuntimeException("Vous n'êtes pas membre de ce projet"));
        boolean authorized = false;
        for (ProjectMember.Role role : allowedRoles) {
            if (member.getRole() == role || member.getRole() == ProjectMember.Role.ADMIN) {
                authorized = true;
                break;
            }
        }
        if (!authorized) throw new RuntimeException("Action non autorisée");
    }

    private String resolveSkeletonForSuite(TestSuite suite, String targetClassName) {
        if (targetClassName == null || targetClassName.isBlank()) return null;
        boolean isUnitOrIntegration = suite.getType() == TestSuite.TestType.UNIT
                || suite.getType() == TestSuite.TestType.INTEGRATION;
        if (!isUnitOrIntegration) return null;

        // gitRepoUrl: suite override > first environment of the project
        String gitRepoUrl = suite.getGitRepoUrl();
        String gitBranch = suite.getGitBranch();
        if (gitRepoUrl == null || gitRepoUrl.isBlank()) {
            // fallback: use first environment with a gitRepoUrl configured
            com.pfe.platform.ms_gestion.entity.Environment env = projectMemberRepository
                    .findByProjectIdAndUserId(suite.getProject().getId(),
                            com.pfe.platform.ms_gestion.security.SecurityUtils.getCurrentUserId())
                    .map(m -> m.getProject())
                    .flatMap(p -> p.getEnvironments().stream()
                            .filter(e -> e.getGitRepoUrl() != null && !e.getGitRepoUrl().isBlank())
                            .findFirst())
                    .orElse(null);
            if (env != null) {
                gitRepoUrl = env.getGitRepoUrl();
                gitBranch = env.getGitBranch();
            }
        }
        if (gitRepoUrl == null || gitRepoUrl.isBlank()) return null;

        try {
            return skeletonExtractorService.extractSkeleton(
                    gitRepoUrl,
                    gitBranch,
                    suite.getModulePath(),
                    targetClassName.trim()
            );
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isValidDatabaseType(String db) {
        if (db == null) return false;
        switch (db.trim().toUpperCase()) {
            case "POSTGRESQL":
            case "MYSQL":
            case "H2":
            case "MONGODB":
                return true;
            default:
                return false;
        }
    }

    private TestCaseResponse mapToResponse(TestCase tc) {
        return TestCaseResponse.builder()
                .id(tc.getId())
                .suiteId(tc.getSuite().getId())
                .title(tc.getTitle())
                .description(tc.getDescription())
                .type(tc.getType().name())
                .springProfile(tc.getSpringProfile())
                .priority(tc.getPriority())
                .riskLevel(tc.getRiskLevel().name())
                .gitRepoUrl(tc.getGitRepoUrl())
                .scriptPath(tc.getScriptPath())
                .testData(tc.getTestData())
                .tags(tc.getTags())
                .maxDurationSeconds(tc.getMaxDurationSeconds())
                .active(tc.getActive())
                .flaky(tc.getFlaky())
                .createdAt(tc.getCreatedAt())
                .generatedCode(tc.getGeneratedCode())
                .generated(tc.getGenerated())
                .databaseType(tc.getDatabaseType())
                .targetClassName(tc.getTargetClassName())
                .build();
    }

    /**
     * Ensures test data is valid JSON. If the input is not valid JSON,
     * wraps it in a JSON object: {"data": "<value>"}
     */
    private String ensureValidJson(String testData) {
        if (testData == null || testData.trim().isEmpty()) {
            return null;
        }

        try {
            // Try to parse as JSON to validate it
            objectMapper.readTree(testData);
            // If successful, it's valid JSON
            return testData;
        } catch (Exception e) {
            // If parsing fails, wrap it as a JSON object
            try {
                return objectMapper.writeValueAsString(java.util.Map.of("data", testData));
            } catch (Exception ex) {
                // Fallback: return as string in JSON format
                return "{\"data\": \"" + testData.replace("\"", "\\\"") + "\"}";
            }
        }
    }

}
