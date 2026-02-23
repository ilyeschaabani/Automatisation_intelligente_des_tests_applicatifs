package com.pfe.platform.testmanagementmicroservice.DTO;

public record RepoResolveResponse(
                                   String owner,
                                   String repo,
                                   String fullName,
                                   String description,
                                   String defaultBranch,
                                   String htmlUrl
) {}
