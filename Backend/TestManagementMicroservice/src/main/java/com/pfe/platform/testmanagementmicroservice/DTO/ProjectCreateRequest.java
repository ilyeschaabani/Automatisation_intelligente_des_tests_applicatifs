package com.pfe.platform.testmanagementmicroservice.DTO;

import com.pfe.platform.testmanagementmicroservice.entity.Enum.ProjectType;
import com.pfe.platform.testmanagementmicroservice.entity.Enum.RepoProvider;

public record ProjectCreateRequest(

                                   String name,
                                   String description,
                                   String repositoryUrl,
                                   ProjectType type,
                                   String defaultBranch,
                                   RepoProvider repoProvider,
                                   Boolean archived
)
{}
