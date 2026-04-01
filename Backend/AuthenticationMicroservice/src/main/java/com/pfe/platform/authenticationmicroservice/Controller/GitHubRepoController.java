package com.pfe.platform.authenticationmicroservice.Controller;

import com.pfe.platform.authenticationmicroservice.Entity.User;
import com.pfe.platform.authenticationmicroservice.Service.Github.GitHubClient;
import com.pfe.platform.authenticationmicroservice.Service.Github.GitHubTokenManager;
import com.pfe.platform.authenticationmicroservice.Service.User.UserService;
import com.pfe.platform.authenticationmicroservice.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GitHubRepoController {

    private final UserService userService;
    private final GitHubClient gitHubClient;
    private final GitHubTokenManager tokenManager;
    private final UserRepository userRepository;

    @GetMapping("/repos")
    public ResponseEntity<?> listRepos(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userService.getUserByEmail(userDetails.getUsername());
        if (!Boolean.TRUE.equals(user.getGithubConnected()) || user.getGithubAccessToken() == null) {
            return ResponseEntity.status(404).body("GitHub not connected");
        }

        String storedToken = user.getGithubAccessToken();
        String accessToken;
        try {
            accessToken = tokenManager.decrypt(storedToken);
        } catch (IllegalStateException ex) {
            // If encrypted but can't be decrypted (wrong key/format), force reconnect.
            return ResponseEntity.status(404).body("GitHub not connected");
        }

        // Auto-migrate plaintext/legacy tokens into encrypted-at-rest format.
        if (!tokenManager.isEncryptedFormat(storedToken)) {
            try {
                user.setGithubAccessToken(tokenManager.encrypt(accessToken));
                userRepository.save(user);
            } catch (Exception ignored) {
                // Non-fatal: repos listing can still proceed using plaintext token.
            }
        }

        try {
            List<Map<String, Object>> repos = gitHubClient.getUserRepos(accessToken);

            // Return a minimal, stable response shape for the frontend.
            // NOTE: We now include `branches` (list of branch names) for the repo selection UI.
            List<Map<String, Object>> payload = repos.stream()
                    .map(r -> Map.<String, Object>of(
                            "name", r.get("name"),
                            "owner", ownerLogin(r.get("owner")),
                            "private", r.get("private"),
                            "html_url", r.get("html_url"),
                            "updated_at", r.get("updated_at"),
                            "branches", extractBranchNames(r.get("branches"))
                    ))
                    .sorted(Comparator.comparing(m -> String.valueOf(m.get("updated_at")), Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();

            return ResponseEntity.ok(payload);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException.Forbidden e) {
            return ResponseEntity.status(403).body("GitHub token lacks repo scope or access is forbidden");
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException.Unauthorized e) {
            return ResponseEntity.status(403).body("Stored GitHub token is invalid or expired. Please reconnect GitHub.");
        }
    }

    @SuppressWarnings("unchecked")
    private static Object ownerLogin(Object ownerObj) {
        if (ownerObj instanceof Map<?, ?> owner) {
            return owner.get("login");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractBranchNames(Object branchesObj) {
        if (!(branchesObj instanceof List<?> branches)) {
            return List.of();
        }
        return branches.stream()
                .filter(b -> b instanceof Map<?, ?>)
                .map(b -> String.valueOf(((Map<String, Object>) b).get("name")))
                .filter(n -> n != null && !n.isBlank() && !"null".equals(n))
                .toList();
    }
}
