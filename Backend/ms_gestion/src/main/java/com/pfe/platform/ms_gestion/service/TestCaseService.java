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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TestCaseService {
    private final TestCaseRepository testCaseRepository;
    private final TestSuiteRepository testSuiteRepository;
    private final ProjectMemberRepository projectMemberRepository;

    private final LlmService llmService;
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
        tc.setPriority(request.getPriority());
        tc.setRiskLevel(TestCase.RiskLevel.valueOf(request.getRiskLevel().toUpperCase()));
        tc.setTestData(ensureValidJson(request.getTestData()));
        tc.setTags(request.getTags());
        tc.setMaxDurationSeconds(request.getMaxDurationSeconds());

        // -------------------------------------------------------
        // Gestion du mode IA / manuel
        // Si le projet parent est en mode IA (pas de gitRepoUrl), on force la génération IA
        // Sinon, conserver le comportement précédent (useAI / generatedCode / scriptPath)
        // -------------------------------------------------------
        boolean projectIsAi = false;
        if (suite.getProject() != null) {
            String repo = suite.getProject().getGitRepoUrl();
            projectIsAi = (repo == null || repo.isBlank());
        }

        if (projectIsAi) {
            // Force generation from LLM using descriptionAI if provided, otherwise use description
            String promptDesc = request.getDescriptionAI() != null && !request.getDescriptionAI().isBlank()
                    ? request.getDescriptionAI()
                    : request.getDescription();
            String generatedCode = llmService.generateTestCode(
                    request.getType(),
                    promptDesc
            );
            tc.setGeneratedCode(generatedCode);
            tc.setGenerated(true);
            // do not set scriptPath
        } else {
            if (Boolean.TRUE.equals(request.getUseAI()) && request.getDescriptionAI() != null) {
                // Cas 1 : régénérer depuis l'IA
                String generatedCode = llmService.generateTestCode(
                        request.getType(),
                        request.getDescriptionAI()
                );
                tc.setGeneratedCode(generatedCode);
                tc.setGenerated(true);
            } else if (request.getGeneratedCode() != null && !request.getGeneratedCode().trim().isEmpty()) {
                // Cas 2 : code généré/édité par l'utilisateur
                tc.setGeneratedCode(request.getGeneratedCode());
                tc.setGenerated(true);
            } else {
                // Cas 3 : mode manuel
                tc.setScriptPath(request.getScriptPath());
                tc.setGenerated(false);
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

        tc.setTitle(request.getTitle());
        tc.setDescription(request.getDescription());
        tc.setType(TestCase.TestType.valueOf(request.getType().toUpperCase()));
        tc.setPriority(request.getPriority());
        tc.setRiskLevel(TestCase.RiskLevel.valueOf(request.getRiskLevel().toUpperCase()));
        tc.setTestData(ensureValidJson(request.getTestData()));
        tc.setTags(request.getTags());
        tc.setMaxDurationSeconds(request.getMaxDurationSeconds());

        // Handle AI mode updates (same three cases as add())
        if (Boolean.TRUE.equals(request.getUseAI()) && request.getDescriptionAI() != null) {
            // Cas 1 : régénérer depuis l'IA
            String generatedCode = llmService.generateTestCode(
                    request.getType(),
                    request.getDescriptionAI()
            );
            tc.setGeneratedCode(generatedCode);
            tc.setGenerated(true);
        } else if (request.getGeneratedCode() != null && !request.getGeneratedCode().trim().isEmpty()) {
            // Cas 2 : code généré/édité par l'utilisateur
            tc.setGeneratedCode(request.getGeneratedCode());
            tc.setGenerated(true);
        } else {
            // Cas 3 : mode manuel
            tc.setScriptPath(request.getScriptPath());
            tc.setGenerated(false);
            tc.setGeneratedCode(null);
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

    private TestCaseResponse mapToResponse(TestCase tc) {
        return TestCaseResponse.builder()
                .id(tc.getId())
                .suiteId(tc.getSuite().getId())
                .title(tc.getTitle())
                .description(tc.getDescription())
                .type(tc.getType().name())
                .priority(tc.getPriority())
                .riskLevel(tc.getRiskLevel().name())
                .scriptPath(tc.getScriptPath())
                .testData(tc.getTestData())
                .tags(tc.getTags())
                .maxDurationSeconds(tc.getMaxDurationSeconds())
                .active(tc.getActive())
                .flaky(tc.getFlaky())
                .createdAt(tc.getCreatedAt())
                .generatedCode(tc.getGeneratedCode())
                .generated(tc.getGenerated())
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
