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
    private final ProjectAccessService projectAccessService;

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (projectRepository.existsByName(request.getName())) {
            throw new RuntimeException("Un projet avec ce nom existe déjà");
        }
        Project project = new Project();
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setCreatedBy(userId);
        project = projectRepository.save(project);

        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUserId(userId);
        projectMemberRepository.save(member);

        return mapToResponse(project);
    }

    public List<ProjectResponse> listMyProjects() {
        if (SecurityUtils.isGlobalAdmin()) {
            return projectRepository.findAll().stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }
        Long userId = SecurityUtils.getCurrentUserId();
        return projectMemberRepository.findByUserId(userId).stream()
                .map(m -> mapToResponse(m.getProject()))
                .collect(Collectors.toList());
    }

    public ProjectResponse getProject(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        projectAccessService.checkMembership(project);
        return mapToResponse(project);
    }

    @Transactional
    public ProjectResponse update(Long projectId, CreateProjectRequest request) {
        Project project = getProjectOrThrow(projectId);
        projectAccessService.checkMembership(project);
        if (!project.getName().equals(request.getName()) &&
                projectRepository.existsByName(request.getName())) {
            throw new RuntimeException("Ce nom est déjà utilisé par un autre projet");
        }
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        return mapToResponse(projectRepository.save(project));
    }

    @Transactional
    public void delete(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        projectAccessService.checkMembership(project);
        projectRepository.delete(project);
    }

    // Méthodes privées
    private Project getProjectOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Projet non trouvé"));
    }

    private ProjectResponse mapToResponse(Project p) {
        return ProjectResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .gitRepoUrl(p.getGitRepoUrl())
                .gitDefaultBranch(p.getGitDefaultBranch())
                .status(p.getStatus().name())
                .createdBy(p.getCreatedBy())
                .createdAt(p.getCreatedAt())
                .aiProject(p.getGitRepoUrl() == null || p.getGitRepoUrl().isBlank() || "ai-builtin".equals(p.getGitRepoUrl()))
                .build();
    }
}
