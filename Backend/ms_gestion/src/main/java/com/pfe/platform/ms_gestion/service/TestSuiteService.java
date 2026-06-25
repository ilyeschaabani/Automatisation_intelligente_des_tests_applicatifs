package com.pfe.platform.ms_gestion.service;

import com.pfe.platform.ms_gestion.dto.request.CreateTestSuiteRequest;
import com.pfe.platform.ms_gestion.dto.response.TestSuiteResponse;
import com.pfe.platform.ms_gestion.entity.Project;
import com.pfe.platform.ms_gestion.entity.TestSuite;
import com.pfe.platform.ms_gestion.repository.ProjectRepository;
import com.pfe.platform.ms_gestion.repository.TestSuiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TestSuiteService {
    private final TestSuiteRepository testSuiteRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;

    @Transactional
    public TestSuiteResponse add(Long projectId, CreateTestSuiteRequest request) {
        Project project = getProjectOrThrow(projectId);
        projectAccessService.checkMembership(project);

        if (testSuiteRepository.existsByProjectIdAndName(projectId, request.getName())) {
            throw new RuntimeException("Une suite avec ce nom existe déjà dans ce projet");
        }

        TestSuite.TestType requestedType = resolveSuiteTypeOrNull(request.getType());
        validateGitFieldsByType(requestedType, request);

        TestSuite suite = new TestSuite();
        suite.setProject(project);
        suite.setName(request.getName());
        suite.setDescription(request.getDescription());
        suite.setGitRepoUrl(request.getGitRepoUrl());
        suite.setGitBranch(request.getGitBranch());
        suite.setModulePath(normalizeModulePathOrNull(request.getModulePath()));
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
        projectAccessService.checkMembership(project);
        return testSuiteRepository.findByProjectId(projectId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public TestSuiteResponse get(Long projectId, Long suiteId) {
        TestSuite suite = getSuiteOrThrow(suiteId, projectId);
        projectAccessService.checkMembership(suite.getProject());
        return mapToResponse(suite);
    }

    @Transactional
    public TestSuiteResponse update(Long projectId, Long suiteId, CreateTestSuiteRequest request) {
        TestSuite suite = getSuiteOrThrow(suiteId, projectId);
        projectAccessService.checkMembership(suite.getProject());

        if (!suite.getName().equals(request.getName()) &&
                testSuiteRepository.existsByProjectIdAndName(projectId, request.getName())) {
            throw new RuntimeException("Une suite avec ce nom existe déjà dans ce projet");
        }

        TestSuite.TestType effectiveType = resolveSuiteTypeOrNull(request.getType());
        if (effectiveType == null) {
            effectiveType = suite.getType();
        }
        validateGitFieldsByType(effectiveType, request);

        suite.setName(request.getName());
        suite.setDescription(request.getDescription());
        suite.setGitRepoUrl(request.getGitRepoUrl());
        suite.setGitBranch(request.getGitBranch());
        suite.setModulePath(normalizeModulePathOrNull(request.getModulePath()));
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
        projectAccessService.checkMembership(suite.getProject());
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

    private TestSuiteResponse mapToResponse(TestSuite s) {
        return TestSuiteResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .description(s.getDescription())
                .type(s.getType() != null ? s.getType().name() : null)
                .gitRepoUrl(s.getGitRepoUrl())
                .gitBranch(s.getGitBranch())
                .modulePath(s.getModulePath())
                .createdAt(s.getCreatedAt())
                .build();
    }

    private String normalizeModulePathOrNull(String modulePath) {
        if (modulePath == null) return null;
        String trimmed = modulePath.trim();
        if (trimmed.isBlank()) return null;

        Path path;
        try {
            path = Paths.get(trimmed);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modulePath is invalid");
        }

        if (path.isAbsolute()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modulePath must be a relative path");
        }

        Path normalized = path.normalize();
        for (Path part : normalized) {
            if ("..".equals(part.toString())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modulePath must not contain '..'");
            }
        }

        // Store a portable representation (forward slashes) so ms-execution can safely resolve it on Linux/Windows.
        return normalized.toString().replace('\\', '/');
    }

    private TestSuite.TestType resolveSuiteTypeOrNull(String type) {
        if (type == null || type.isBlank()) return null;
        try {
            return TestSuite.TestType.valueOf(type.trim().toUpperCase());
        } catch (Exception ignored) {
            return null;
        }
    }

    private void validateGitFieldsByType(TestSuite.TestType type, CreateTestSuiteRequest request) {
        // gitRepoUrl and gitBranch are now configured at Environment level.
        // They are optional overrides at suite level (e.g. for multi-repo projects).
        // modulePath is still validated if the suite has its own gitRepoUrl.
        if (type == null) return;
        boolean hasSuiteRepo = request.getGitRepoUrl() != null && !request.getGitRepoUrl().isBlank();
        if (hasSuiteRepo && (request.getGitBranch() == null || request.getGitBranch().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "gitBranch is required when gitRepoUrl is specified on the suite");
        }
    }
}
