package com.pfe.platform.ms_gestion.service;


import com.pfe.platform.ms_gestion.dto.request.AddMemberRequest;
import com.pfe.platform.ms_gestion.dto.response.MemberResponse;
import com.pfe.platform.ms_gestion.entity.Project;
import com.pfe.platform.ms_gestion.entity.ProjectMember;
import com.pfe.platform.ms_gestion.repository.ProjectMemberRepository;
import com.pfe.platform.ms_gestion.repository.ProjectRepository;
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
    private final ProjectAccessService projectAccessService;

    @Transactional
    public void addMember(Long projectId, AddMemberRequest request) {
        Project project = getProjectOrThrow(projectId);
        projectAccessService.checkMembership(project);

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, request.getUserId())) {
            throw new RuntimeException("Cet utilisateur est déjà membre du projet");
        }

        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUserId(request.getUserId());
        projectMemberRepository.save(member);
    }

    public List<MemberResponse> listMembers(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        projectAccessService.checkMembership(project);
        return projectMemberRepository.findByProjectId(projectId).stream()
                .map(m -> MemberResponse.builder()
                        .userId(m.getUserId())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeMember(Long projectId, Long userId) {
        Project project = getProjectOrThrow(projectId);
        projectAccessService.checkMembership(project);
        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new RuntimeException("Membre non trouvé"));
        projectMemberRepository.delete(member);
    }

    private Project getProjectOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Projet non trouvé"));
    }

}
