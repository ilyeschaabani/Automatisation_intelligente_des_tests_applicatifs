package com.pfe.platform.ms_gestion.service;


import com.pfe.platform.ms_gestion.dto.request.AddMemberRequest;
import com.pfe.platform.ms_gestion.dto.response.MemberResponse;
import com.pfe.platform.ms_gestion.entity.Notification;
import com.pfe.platform.ms_gestion.entity.Project;
import com.pfe.platform.ms_gestion.entity.ProjectMember;
import com.pfe.platform.ms_gestion.entity.UserRef;
import com.pfe.platform.ms_gestion.repository.ProjectMemberRepository;
import com.pfe.platform.ms_gestion.repository.ProjectRepository;
import com.pfe.platform.ms_gestion.repository.UserRefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessService projectAccessService;
    private final UserRefRepository userRefRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @Transactional
    public void addMember(Long projectId, AddMemberRequest request) {
        Project project = getProjectOrThrow(projectId);
        projectAccessService.checkMembership(project);

        Long userId = resolveUserId(request);

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new RuntimeException("Cet utilisateur est déjà membre du projet");
        }

        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUserId(userId);
        projectMemberRepository.save(member);

        UserRef user = userRefRepository.findById(userId).orElse(null);
        if (user != null && user.getEmail() != null) {
            String displayName = buildDisplayName(user);

            notificationService.create(
                    userId,
                    "Ajouté au projet",
                    "Vous avez été ajouté au projet « " + project.getName() + " ».",
                    Notification.NotifType.PROJECT_MEMBER_ADDED,
                    "/projects/" + projectId
            );

            emailService.sendProjectMemberEmail(user.getEmail(), displayName, project.getName(), projectId);
        }
    }

    private Long resolveUserId(AddMemberRequest request) {
        if (request.getUserId() != null) {
            return request.getUserId();
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String email = request.getEmail().trim().toLowerCase();
            UserRef user = userRefRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Aucun utilisateur trouvé avec l'email : " + email));
            return user.getId();
        }
        throw new RuntimeException("userId ou email requis");
    }

    private String buildDisplayName(UserRef user) {
        String name = "";
        if (user.getPrenom() != null) name += user.getPrenom();
        if (user.getNom() != null) name += (name.isEmpty() ? "" : " ") + user.getNom();
        return name.isEmpty() ? user.getEmail() : name;
    }

    public List<MemberResponse> listMembers(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        projectAccessService.checkMembership(project);

        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        List<Long> userIds = members.stream().map(ProjectMember::getUserId).collect(Collectors.toList());
        Map<Long, UserRef> usersById = userRefRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(UserRef::getId, Function.identity()));

        return members.stream()
                .map(m -> {
                    UserRef user = usersById.get(m.getUserId());
                    MemberResponse.MemberResponseBuilder builder = MemberResponse.builder()
                            .userId(m.getUserId());
                    if (user != null) {
                        builder.nom(user.getNom())
                               .prenom(user.getPrenom())
                               .email(user.getEmail())
                               .imageUrl(user.getImageUrl());
                    }
                    return builder.build();
                })
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
