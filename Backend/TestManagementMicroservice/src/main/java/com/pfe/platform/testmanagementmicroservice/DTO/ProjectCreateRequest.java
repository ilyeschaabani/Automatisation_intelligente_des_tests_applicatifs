package com.pfe.platform.testmanagementmicroservice.DTO;

import com.pfe.platform.testmanagementmicroservice.entity.Enum.ProjectType;
import com.pfe.platform.testmanagementmicroservice.entity.Enum.SourceType;

public record ProjectCreateRequest(
        String name,
        ProjectType projectType,
        SourceType sourceType,
        String repositoryUrl,
        String gitTokenId,
        String technologyStack,
        String defaultBranch,
        Boolean deployed
) {
}
