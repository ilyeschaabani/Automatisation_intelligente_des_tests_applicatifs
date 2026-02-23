package com.pfe.platform.testmanagementmicroservice.controller;


import com.pfe.platform.testmanagementmicroservice.DTO.GitHubRepoResponse;
import com.pfe.platform.testmanagementmicroservice.DTO.GitLabRepoResponse;
import com.pfe.platform.testmanagementmicroservice.DTO.RepoResolveRequest;
import com.pfe.platform.testmanagementmicroservice.DTO.RepoResolveResponse;
import com.pfe.platform.testmanagementmicroservice.entity.Enum.RepoProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@RestController
@RequestMapping("/api/repo")
@RequiredArgsConstructor
public class RepoController {

    private final RestTemplate restTemplate;

    @PostMapping("/resolve")
    public RepoResolveResponse resolve(@Validated @RequestBody RepoResolveRequest req) {
        if (req.provider() == RepoProvider.GITHUB) {
            return resolveGitHub(req.repositoryUrl());
        }
        if (req.provider() == RepoProvider.GITLAB) {
            return resolveGitLab(req.repositoryUrl());
        }
        throw new IllegalArgumentException("Unsupported provider");
    }
    private RepoResolveResponse resolveGitHub(String repositoryUrl) {
        URI uri = URI.create(repositoryUrl.trim());
        if (!"github.com".equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException("Not a github.com URL");
        }

        String[] parts = uri.getPath().split("/");
        String owner = part(parts, 1).trim();
        String repo = normalizeRepoName(part(parts, 2));
        if (owner.isBlank() || repo.isBlank()) throw new IllegalArgumentException("Invalid GitHub URL");

        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo;
        ResponseEntity<GitHubRepoResponse> response = restTemplate.getForEntity(apiUrl, GitHubRepoResponse.class);

        GitHubRepoResponse repoData = response.getBody();
        if (repoData == null) throw new IllegalArgumentException("Failed to fetch GitHub repository data");

        return new RepoResolveResponse(
                owner,
                repo,
                repoData.full_name(),
                repoData.description(),
                repoData.default_branch(),
                repoData.html_url()
        );
    }



    private RepoResolveResponse resolveGitLab(String repositoryUrl) {
        URI uri = URI.create(repositoryUrl.trim());
        String host = uri.getHost();
        if (host == null || !host.toLowerCase().contains("gitlab")) {
            throw new IllegalArgumentException("Not a GitLab URL");
        }

        String[] parts = uri.getPath().split("/");
        String owner = part(parts, 1).trim();
        String repo = normalizeRepoName(part(parts, 2));
        if (owner.isBlank() || repo.isBlank()) throw new IllegalArgumentException("Invalid GitLab URL");

        String apiUrl = "https://gitlab.com/api/v4/projects/" + owner + "%2F" + repo;
        ResponseEntity<GitLabRepoResponse> response = restTemplate.getForEntity(apiUrl, GitLabRepoResponse.class);

        GitLabRepoResponse repoData = response.getBody();
        if (repoData == null) throw new IllegalArgumentException("Failed to fetch GitLab repository data");

        return new RepoResolveResponse(
                owner,
                repo,
                repoData.path_with_namespace(),
                repoData.description(),
                repoData.default_branch(),
                repoData.web_url()
        );
    }


    private static String part(String[] parts, int index) {
        return index >= 0 && index < parts.length ? parts[index] : "";
    }

    private static String normalizeRepoName(String raw) {
        if (raw == null) return "";
        String v = raw.trim();
        // remove trailing slashes
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        // remove optional .git suffix
        v = v.replaceAll("\\.git$", "");
        return v;
    }

    private static String stripGit(String name) {
        // keep for compatibility if used elsewhere
        return name == null ? "" : name.replaceAll("\\.git$", "");
    }
}
