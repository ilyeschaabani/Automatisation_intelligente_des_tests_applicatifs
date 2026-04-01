package com.pfe.platform.testmanagementmicroservice.DTO;

import com.pfe.platform.testmanagementmicroservice.entity.Enum.ProjectType;
import com.pfe.platform.testmanagementmicroservice.entity.Enum.SourceType;

import java.time.Instant;

public record ProjectDto(
        Long id,
        String name,
        ProjectType projectType,
        SourceType sourceType,
        String repositoryUrl,
        String gitTokenId,
        String technologyStack,
        String defaultBranch,
        boolean deployed,
        Instant createdAt
) {
}
