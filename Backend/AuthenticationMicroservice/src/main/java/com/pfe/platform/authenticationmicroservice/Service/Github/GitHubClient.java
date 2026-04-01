package com.pfe.platform.authenticationmicroservice.Service.Github;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GitHubClient {

    private final WebClient webClient;

    public GitHubClient(WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public Map<String, Object> getUser(String accessToken) {
        return webClient.get()
                .uri("/user")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getUserRepos(String accessToken) {
        List<Map<String, Object>> repos = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/user/repos")
                        .queryParam("per_page", 100)
                        .queryParam("sort", "updated")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (repos == null) {
            return List.of();
        }

        List<Map<String, Object>> enriched = new ArrayList<>(repos.size());

        for (Map<String, Object> repo : repos) {
            Map<String, Object> copy = new HashMap<>(repo);

            Map<String, Object> ownerObj = (Map<String, Object>) repo.get("owner");
            String ownerLogin = ownerObj != null ? (String) ownerObj.get("login") : null;
            String repoName = (String) repo.get("name");

            if (ownerLogin != null && repoName != null) {
                List<Map<String, Object>> branches = getRepoBranches(accessToken, ownerLogin, repoName);
                copy.put("branches", branches);
            } else {
                copy.put("branches", List.of());
            }

            enriched.add(copy);
        }

        return enriched;
    }
    public List<Map<String, Object>> getRepoBranches(String accessToken, String owner, String repo) {
        List<Map<String, Object>> branches = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/branches")
                        .queryParam("per_page", 100)
                        .build(owner, repo))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(List.class)
                .block();

        return branches != null ? branches : List.of();
    }
}

