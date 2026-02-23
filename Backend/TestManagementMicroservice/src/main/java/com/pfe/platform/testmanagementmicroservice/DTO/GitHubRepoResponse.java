package com.pfe.platform.testmanagementmicroservice.DTO;

public record GitHubRepoResponse(
        String full_name,
        String description,
        String default_branch,
        String html_url
) {
}
