package com.pfe.platform.msexecution.service;

import com.pfe.platform.msexecution.repository.ProjectMemberRepository;
import com.pfe.platform.msexecution.repository.ProjectRepository;
import com.pfe.platform.msexecution.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectAccessService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;

    public void checkMembership(Long projectId) {
        if (projectId == null) return;
        if (SecurityUtils.isGlobalAdmin()) return;
        Long userId = SecurityUtils.getCurrentUserId();
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new RuntimeException("Vous n'êtes pas membre de ce projet");
        }
    }

    public List<Long> getAccessibleProjectIds() {
        if (SecurityUtils.isGlobalAdmin()) {
            return projectRepository.findAll().stream()
                    .map(p -> p.getId()).toList();
        }
        Long userId = SecurityUtils.getCurrentUserId();
        return projectMemberRepository.findByUserId(userId).stream()
                .map(m -> m.getProjectId()).toList();
    }
}
