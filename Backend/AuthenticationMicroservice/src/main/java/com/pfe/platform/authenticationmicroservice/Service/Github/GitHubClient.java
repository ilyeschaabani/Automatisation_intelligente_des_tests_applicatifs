package com.pfe.platform.authenticationmicroservice.Service.Github;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GitHubClient {

    private final WebClient webClient;

    public GitHubClient(WebClient.Builder builder) {
        // Configure SSL to trust all certificates (handles corporate proxies & self-signed certs)
        HttpClient httpClient;
        try {
            SslContext sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            httpClient = HttpClient.create()
                    .secure(spec -> spec.sslContext(sslContext));
        } catch (SSLException e) {
            httpClient = HttpClient.create(); // fallback to default
        }

        this.webClient = builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
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

    /**
     * Returns the flat file tree of a repo (recursive) filtered to Java source files.
     * Uses /git/trees/{branch}?recursive=1 — lightweight, no clone needed.
     */
    @SuppressWarnings("unchecked")
    public List<String> getJavaSourceFiles(String accessToken, String owner, String repo, String branch) {
        Map<String, Object> tree = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/git/trees/{branch}")
                        .queryParam("recursive", "1")
                        .build(owner, repo, branch != null ? branch : "main"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (tree == null) return List.of();

        List<Map<String, Object>> items = (List<Map<String, Object>>) tree.get("tree");
        if (items == null) return List.of();

        return items.stream()
                .filter(item -> "blob".equals(item.get("type")))
                .map(item -> (String) item.get("path"))
                .filter(path -> path != null
                        && path.endsWith(".java")
                        && path.contains("src/main/java"))
                .sorted()
                .toList();
    }

    /**
     * Returns the raw (decoded) content of a single file.
     * Uses /contents/{path}?ref={branch} — single HTTP request.
     */
    @SuppressWarnings("unchecked")
    public String getFileContent(String accessToken, String owner, String repo, String path, String branch) {
        Map<String, Object> response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/contents/{path}")
                        .queryParam("ref", branch != null ? branch : "main")
                        .build(owner, repo, path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) return null;

        String encoded = (String) response.get("content");
        if (encoded == null) return null;

        // GitHub returns base64 content with newlines — clean and decode
        String clean = encoded.replaceAll("\\s", "");
        byte[] decoded = java.util.Base64.getDecoder().decode(clean);
        return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
    }
}

