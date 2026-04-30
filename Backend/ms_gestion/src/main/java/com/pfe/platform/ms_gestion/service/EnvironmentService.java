package com.pfe.platform.ms_gestion.service;

import com.pfe.platform.ms_gestion.dto.request.CreateEnvironmentRequest;
import com.pfe.platform.ms_gestion.dto.response.EnvironmentResponse;
import com.pfe.platform.ms_gestion.entity.Environment;
import com.pfe.platform.ms_gestion.entity.Project;
import com.pfe.platform.ms_gestion.entity.ProjectMember;
import com.pfe.platform.ms_gestion.repository.EnvironmentRepository;
import com.pfe.platform.ms_gestion.repository.ProjectMemberRepository;
import com.pfe.platform.ms_gestion.repository.ProjectRepository;
import com.pfe.platform.ms_gestion.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnvironmentService {
    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional
    public EnvironmentResponse add(Long projectId, CreateEnvironmentRequest request) {
        Project project = getProjectOrThrow(projectId);
        checkProjectRole(project, ProjectMember.Role.ADMIN, ProjectMember.Role.TESTER);

        if (environmentRepository.existsByProjectIdAndName(projectId, request.getName())) {
            throw new RuntimeException("Un environnement avec ce nom existe déjà dans ce projet");
        }

        Environment env = new Environment();
        env.setProject(project);
        env.setName(request.getName());
        env.setBaseUrlWeb(request.getBaseUrlWeb());
        env.setBaseUrlApi(request.getBaseUrlApi());
        env.setVariables(request.getVariables());
        env = environmentRepository.save(env);
        return mapToResponse(env);
    }

    public List<EnvironmentResponse> list(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        checkMembership(project);
        return environmentRepository.findByProjectId(projectId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public EnvironmentResponse get(Long projectId, Long envId) {
        Environment env = getEnvOrThrow(envId, projectId);
        checkMembership(env.getProject());
        return mapToResponse(env);
    }

    @Transactional
    public EnvironmentResponse update(Long projectId, Long envId, CreateEnvironmentRequest request) {
        Environment env = getEnvOrThrow(envId, projectId);
        checkProjectRole(env.getProject(), ProjectMember.Role.ADMIN, ProjectMember.Role.TESTER);

        if (!env.getName().equals(request.getName()) &&
                environmentRepository.existsByProjectIdAndName(projectId, request.getName())) {
            throw new RuntimeException("Un environnement avec ce nom existe déjà dans ce projet");
        }

        env.setName(request.getName());
        env.setBaseUrlWeb(request.getBaseUrlWeb());
        env.setBaseUrlApi(request.getBaseUrlApi());
        env.setVariables(request.getVariables());
        return mapToResponse(environmentRepository.save(env));
    }

    @Transactional
    public void delete(Long projectId, Long envId) {
        Environment env = getEnvOrThrow(envId, projectId);
        checkProjectRole(env.getProject(), ProjectMember.Role.ADMIN);
        environmentRepository.delete(env);
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

    private Environment getEnvOrThrow(Long envId, Long projectId) {
        Environment env = environmentRepository.findById(envId)
                .orElseThrow(() -> new RuntimeException("Environnement non trouvé"));
        if (!env.getProject().getId().equals(projectId)) {
            throw new RuntimeException("L'environnement n'appartient pas à ce projet");
        }
        return env;
    }

    private Project getProjectOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Projet non trouvé"));
    }

    private EnvironmentResponse mapToResponse(Environment env) {
        return EnvironmentResponse.builder()
                .id(env.getId())
                .name(env.getName())
                .baseUrlWeb(env.getBaseUrlWeb())
                .baseUrlApi(env.getBaseUrlApi())
                .variables(env.getVariables())
                .createdAt(env.getCreatedAt())
                .build();
    }
}
