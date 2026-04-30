package com.pfe.platform.ms_gestion.service;


import com.pfe.platform.ms_gestion.dto.request.AddMemberRequest;
import com.pfe.platform.ms_gestion.dto.response.MemberResponse;
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
public class ProjectMemberService {
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional
    public void addMember(Long projectId, AddMemberRequest request) {
        Project project = getProjectOrThrow(projectId);
        checkRole(project, ProjectMember.Role.ADMIN);

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, request.getUserId())) {
            throw new RuntimeException("Cet utilisateur est déjà membre du projet");
        }

        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUserId(request.getUserId());
        member.setRole(ProjectMember.Role.valueOf(request.getRole().toUpperCase()));
        projectMemberRepository.save(member);
    }

    public List<MemberResponse> listMembers(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        checkMembership(project);
        return projectMemberRepository.findByProjectId(projectId).stream()
                .map(m -> MemberResponse.builder()
                        .userId(m.getUserId())
                        .role(m.getRole().name())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeMember(Long projectId, Long userId) {
        Project project = getProjectOrThrow(projectId);
        checkRole(project, ProjectMember.Role.ADMIN);
        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new RuntimeException("Membre non trouvé"));
        projectMemberRepository.delete(member);
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

    private void checkRole(Project project, ProjectMember.Role requiredRole) {
        Long userId = SecurityUtils.getCurrentUserId();
        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserId(project.getId(), userId)
                .orElseThrow(() -> new RuntimeException("Vous n'êtes pas membre de ce projet"));
        if (member.getRole() != requiredRole && member.getRole() != ProjectMember.Role.ADMIN) {
            throw new RuntimeException("Action non autorisée");
        }
    }
}
