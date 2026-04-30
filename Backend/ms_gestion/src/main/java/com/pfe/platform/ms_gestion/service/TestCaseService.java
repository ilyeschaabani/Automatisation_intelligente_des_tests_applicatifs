package com.pfe.platform.ms_gestion.service;


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
        tc.setScriptPath(request.getScriptPath());
        tc.setTestData(request.getTestData());
        tc.setTags(request.getTags());
        tc.setMaxDurationSeconds(request.getMaxDurationSeconds());
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
        tc.setScriptPath(request.getScriptPath());
        tc.setTestData(request.getTestData());
        tc.setTags(request.getTags());
        tc.setMaxDurationSeconds(request.getMaxDurationSeconds());
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
                .build();
    }

}
