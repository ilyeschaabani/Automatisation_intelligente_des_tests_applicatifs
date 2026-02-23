package com.pfe.platform.testmanagementmicroservice.DTO;

public record GitLabRepoResponse(

        String path_with_namespace,
        String description,
        String default_branch,
        String web_url
) {
}
