package com.pfe.platform.testmanagementmicroservice.service.Project;

import com.pfe.platform.testmanagementmicroservice.DTO.ProjectDto;
import com.pfe.platform.testmanagementmicroservice.entity.Project;

public final class ProjectMapper {

    private ProjectMapper() {
    }

    public static ProjectDto toDto(Project p) {
        return new ProjectDto(
                p.getId(),
                p.getName(),
                p.getProjectType(),
                p.getSourceType(),
                p.getRepositoryUrl(),
                p.getGitTokenId(),
                p.getTechnologyStack(),
                p.getDefaultBranch(),
                p.isDeployed(),
                p.getCreatedAt()
        );
    }
}
