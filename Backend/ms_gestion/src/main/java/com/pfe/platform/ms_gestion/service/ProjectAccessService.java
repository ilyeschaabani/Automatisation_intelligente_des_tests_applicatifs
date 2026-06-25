package com.pfe.platform.ms_gestion.service;

import com.pfe.platform.ms_gestion.entity.Project;
import com.pfe.platform.ms_gestion.repository.ProjectMemberRepository;
import com.pfe.platform.ms_gestion.repository.ProjectRepository;
import com.pfe.platform.ms_gestion.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectAccessService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;

    public void checkMembership(Project project) {
        if (SecurityUtils.isGlobalAdmin()) return;
        Long userId = SecurityUtils.getCurrentUserId();
        if (!projectMemberRepository.existsByProjectIdAndUserId(project.getId(), userId)) {
            throw new RuntimeException("Vous n'êtes pas membre de ce projet");
        }
    }

    public void checkMembership(Long projectId) {
        if (SecurityUtils.isGlobalAdmin()) return;
        Long userId = SecurityUtils.getCurrentUserId();
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new RuntimeException("Vous n'êtes pas membre de ce projet");
        }
    }

    public List<Long> getAccessibleProjectIds() {
        if (SecurityUtils.isGlobalAdmin()) {
            return projectRepository.findAll().stream()
                    .map(Project::getId)
                    .toList();
        }
        Long userId = SecurityUtils.getCurrentUserId();
        return projectMemberRepository.findByUserId(userId).stream()
                .map(m -> m.getProject().getId())
                .toList();
    }
}
