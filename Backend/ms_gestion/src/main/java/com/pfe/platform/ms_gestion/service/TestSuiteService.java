package com.pfe.platform.ms_gestion.service;

import com.pfe.platform.ms_gestion.dto.request.CreateTestSuiteRequest;
import com.pfe.platform.ms_gestion.dto.response.TestSuiteResponse;
import com.pfe.platform.ms_gestion.entity.Project;
import com.pfe.platform.ms_gestion.entity.ProjectMember;
import com.pfe.platform.ms_gestion.entity.TestSuite;
import com.pfe.platform.ms_gestion.repository.ProjectMemberRepository;
import com.pfe.platform.ms_gestion.repository.ProjectRepository;
import com.pfe.platform.ms_gestion.repository.TestSuiteRepository;
import com.pfe.platform.ms_gestion.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TestSuiteService {
    private final TestSuiteRepository testSuiteRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional
    public TestSuiteResponse add(Long projectId, CreateTestSuiteRequest request) {
        Project project = getProjectOrThrow(projectId);
        checkProjectRole(project, ProjectMember.Role.ADMIN, ProjectMember.Role.TESTER);

        if (testSuiteRepository.existsByProjectIdAndName(projectId, request.getName())) {
            throw new RuntimeException("Une suite avec ce nom existe déjà dans ce projet");
        }

        // Validate: if suite type is UNIT, gitRepoUrl and gitBranch are required
        if (request.getType() != null && "UNIT".equalsIgnoreCase(request.getType())) {
            if (request.getGitRepoUrl() == null || request.getGitRepoUrl().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gitRepoUrl is required when suite type is UNIT");
            }
            if (request.getGitBranch() == null || request.getGitBranch().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gitBranch is required when suite type is UNIT");
            }
        }

        TestSuite suite = new TestSuite();
        suite.setProject(project);
        suite.setName(request.getName());
        suite.setDescription(request.getDescription());
        suite.setGitRepoUrl(request.getGitRepoUrl());
        suite.setGitBranch(request.getGitBranch());
        if (request.getType() != null) {
            try {
                suite.setType(TestSuite.TestType.valueOf(request.getType().toUpperCase()));
            } catch (Exception ignored) {
            }
        }
        suite = testSuiteRepository.save(suite);
        return mapToResponse(suite);
    }

    public List<TestSuiteResponse> list(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        checkMembership(project);
        return testSuiteRepository.findByProjectId(projectId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public TestSuiteResponse get(Long projectId, Long suiteId) {
        TestSuite suite = getSuiteOrThrow(suiteId, projectId);
        checkMembership(suite.getProject());
        return mapToResponse(suite);
    }

    @Transactional
    public TestSuiteResponse update(Long projectId, Long suiteId, CreateTestSuiteRequest request) {
        TestSuite suite = getSuiteOrThrow(suiteId, projectId);
        checkProjectRole(suite.getProject(), ProjectMember.Role.ADMIN, ProjectMember.Role.TESTER);

        if (!suite.getName().equals(request.getName()) &&
                testSuiteRepository.existsByProjectIdAndName(projectId, request.getName())) {
            throw new RuntimeException("Une suite avec ce nom existe déjà dans ce projet");
        }

        // Validate: if suite type is UNIT, gitRepoUrl and gitBranch are required
        if (request.getType() != null && "UNIT".equalsIgnoreCase(request.getType())) {
            if (request.getGitRepoUrl() == null || request.getGitRepoUrl().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gitRepoUrl is required when suite type is UNIT");
            }
            if (request.getGitBranch() == null || request.getGitBranch().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gitBranch is required when suite type is UNIT");
            }
        }

        suite.setName(request.getName());
        suite.setDescription(request.getDescription());
        suite.setGitRepoUrl(request.getGitRepoUrl());
        suite.setGitBranch(request.getGitBranch());
        if (request.getType() != null) {
            try {
                suite.setType(TestSuite.TestType.valueOf(request.getType().toUpperCase()));
            } catch (Exception ignored) {
            }
        }
        return mapToResponse(testSuiteRepository.save(suite));
    }

    @Transactional
    public void delete(Long projectId, Long suiteId) {
        TestSuite suite = getSuiteOrThrow(suiteId, projectId);
        checkProjectRole(suite.getProject(), ProjectMember.Role.ADMIN);
        testSuiteRepository.delete(suite);
    }

    private TestSuite getSuiteOrThrow(Long suiteId, Long projectId) {
        TestSuite suite = testSuiteRepository.findById(suiteId)
                .orElseThrow(() -> new RuntimeException("Suite non trouvée"));
        if (!suite.getProject().getId().equals(projectId)) {
            throw new RuntimeException("La suite n'appartient pas à ce projet");
        }
        return suite;
    }

    private Project getProjectOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Projet non trouvé"));
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

    private TestSuiteResponse mapToResponse(TestSuite s) {
        return TestSuiteResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .description(s.getDescription())
                .type(s.getType() != null ? s.getType().name() : null)
                .gitRepoUrl(s.getGitRepoUrl())
                .gitBranch(s.getGitBranch())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
