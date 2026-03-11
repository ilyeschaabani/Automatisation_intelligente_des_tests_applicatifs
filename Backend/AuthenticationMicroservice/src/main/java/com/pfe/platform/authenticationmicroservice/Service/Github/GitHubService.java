package com.pfe.platform.authenticationmicroservice.Service.Github;

import com.pfe.platform.authenticationmicroservice.Entity.User;
import com.pfe.platform.authenticationmicroservice.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GitHubService {

    private final UserRepository userRepository;
    private final GitHubTokenManager tokenManager;

    private final WebClient webClient = WebClient.builder().build();

    @Value("${github.oauth.client-id}")
    private String clientId;

    @Value("${github.oauth.client-secret}")
    private String clientSecret;

    @Value("${github.oauth.redirect-uri}")
    private String redirectUri;

    @Value("${github.oauth.scope:repo read:user user:email}")
    private String scope;

    public String buildAuthorizeUrl(String state) {
        return "https://github.com/login/oauth/authorize" +
                "?client_id=" + clientId +
                "&redirect_uri=" + urlEncode(redirectUri) +
                "&scope=" + urlEncode(scope) +
                "&state=" + urlEncode(state);
    }

    public String exchangeCodeForAccessToken(String code) {
        Map<String, Object> resp = webClient.post()
                .uri("https://github.com/login/oauth/access_token")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("client_id", clientId)
                        .with("client_secret", clientSecret)
                        .with("code", code)
                        .with("redirect_uri", redirectUri))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (resp == null || resp.get("access_token") == null) {
            throw new IllegalStateException("GitHub token exchange failed: " + resp);
        }
        return String.valueOf(resp.get("access_token"));
    }

    public User linkGitHubAccount(User currentUser, String accessToken, Map<String, Object> githubUser) {
        String githubId = githubUser.get("id") == null ? null : String.valueOf(githubUser.get("id"));
        String login = githubUser.get("login") == null ? null : String.valueOf(githubUser.get("login"));
        String avatarUrl = githubUser.get("avatar_url") == null ? null : String.valueOf(githubUser.get("avatar_url"));

        if (githubId == null || githubId.isBlank()) {
            throw new IllegalStateException("GitHub user id missing");
        }

        Optional<User> existingByGithubId = userRepository.findByGithubId(githubId);
        if (existingByGithubId.isPresent() && !existingByGithubId.get().getId().equals(currentUser.getId())) {
            throw new IllegalStateException("This GitHub account is already linked to another user");
        }

        currentUser.setGithubId(githubId);
        currentUser.setGithubUsername(login);
        currentUser.setGithubAvatarUrl(avatarUrl);
        currentUser.setGithubConnected(Boolean.TRUE);
        currentUser.setGithubAccessToken(tokenManager.encrypt(accessToken));
        currentUser.setGithubTokenCreatedAt(Instant.now());

        return userRepository.save(currentUser);
    }

    private static String urlEncode(String v) {
        return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
    }
}
