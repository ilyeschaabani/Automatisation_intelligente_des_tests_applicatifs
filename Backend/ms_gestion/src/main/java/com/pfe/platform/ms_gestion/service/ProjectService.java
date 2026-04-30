package com.pfe.platform.ms_gestion.service;

import com.pfe.platform.ms_gestion.dto.request.CreateProjectRequest;
import com.pfe.platform.ms_gestion.dto.response.ProjectResponse;
import com.pfe.platform.ms_gestion.entity.Project;
import com.pfe.platform.ms_gestion.entity.ProjectMember;
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
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (projectRepository.existsByName(request.getName())) {
            throw new RuntimeException("Un projet avec ce nom existe déjà");
        }
        Project project = new Project();
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setGitRepoUrl(request.getGitRepoUrl());
        project.setGitDefaultBranch(request.getGitDefaultBranch());
        project = projectRepository.save(project);

        // Ajouter le créateur comme ADMIN
        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUserId(userId);
        member.setRole(ProjectMember.Role.ADMIN);
        projectMemberRepository.save(member);

        return mapToResponse(project);
    }

    public List<ProjectResponse> listMyProjects() {
        Long userId = SecurityUtils.getCurrentUserId();
        return projectMemberRepository.findByUserId(userId).stream()
                .map(m -> mapToResponse(m.getProject()))
                .collect(Collectors.toList());
    }

    public ProjectResponse getProject(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        checkMembership(project);
        return mapToResponse(project);
    }

    @Transactional
    public ProjectResponse update(Long projectId, CreateProjectRequest request) {
        Project project = getProjectOrThrow(projectId);
        checkRole(project, ProjectMember.Role.ADMIN);
        if (!project.getName().equals(request.getName()) &&
                projectRepository.existsByName(request.getName())) {
            throw new RuntimeException("Ce nom est déjà utilisé par un autre projet");
        }
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setGitRepoUrl(request.getGitRepoUrl());
        project.setGitDefaultBranch(request.getGitDefaultBranch());
        return mapToResponse(projectRepository.save(project));
    }

    @Transactional
    public void archive(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        checkRole(project, ProjectMember.Role.ADMIN);
        project.setStatus(Project.Status.ARCHIVED);
        projectRepository.save(project);
    }

    // Méthodes privées
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

    private void checkRole(Project project, ProjectMember.Role requiredRole) {
        Long userId = SecurityUtils.getCurrentUserId();
        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserId(project.getId(), userId)
                .orElseThrow(() -> new RuntimeException("Vous n'êtes pas membre de ce projet"));
        if (member.getRole() != requiredRole && member.getRole() != ProjectMember.Role.ADMIN) {
            throw new RuntimeException("Action non autorisée");
        }
    }

    private ProjectResponse mapToResponse(Project p) {
        return ProjectResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .gitRepoUrl(p.getGitRepoUrl())
                .gitDefaultBranch(p.getGitDefaultBranch())
                .status(p.getStatus().name())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
