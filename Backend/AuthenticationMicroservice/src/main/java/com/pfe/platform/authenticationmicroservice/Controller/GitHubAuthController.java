package com.pfe.platform.authenticationmicroservice.Controller;

import com.pfe.platform.authenticationmicroservice.Config.CookieUtil;
import com.pfe.platform.authenticationmicroservice.Entity.User;
import com.pfe.platform.authenticationmicroservice.Service.Github.GitHubClient;
import com.pfe.platform.authenticationmicroservice.Service.Github.GitHubService;
import com.pfe.platform.authenticationmicroservice.Service.User.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;

@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GitHubAuthController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GitHubService gitHubService;
    private final GitHubClient gitHubClient;
    private final UserService userService;

    @Value("${app.cookies.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookies.same-site:Lax}")
    private String cookieSameSite;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @GetMapping("/connect")
    public ResponseEntity<Void> connect(HttpServletResponse response) {
        String state = generateState();
        // Short-lived state cookie (10 min)
        CookieUtil.addHttpOnlyCookie(response, "github_oauth_state", state, 60 * 10, cookieSecure, cookieSameSite);

        String authorizeUrl = gitHubService.buildAuthorizeUrl(state);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, authorizeUrl).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("code") String code,
                                         @RequestParam("state") String state,
                                         HttpServletRequest request,
                                         HttpServletResponse response,
                                         @AuthenticationPrincipal UserDetails userDetails) {

        String expectedState = CookieUtil.getCookieValue(request, "github_oauth_state");
        CookieUtil.clearCookie(response, "github_oauth_state", cookieSecure, cookieSameSite);
        if (expectedState == null || !expectedState.equals(state)) {
            return ResponseEntity.status(400).build();
        }

        if (userDetails == null) {
            // Linking GitHub requires an authenticated platform user
            return ResponseEntity.status(401).build();
        }

        User currentUser = userService.getUserByEmail(userDetails.getUsername());

        String accessToken = gitHubService.exchangeCodeForAccessToken(code);
        var githubUser = gitHubClient.getUser(accessToken);
        gitHubService.linkGitHubAccount(currentUser, accessToken, githubUser);

        // Redirect back to frontend profile page
        String redirect = frontendBaseUrl + "/profile?github=connected";
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, redirect).build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> githubMe(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        User user = userService.getUserByEmail(userDetails.getUsername());
        if (!Boolean.TRUE.equals(user.getGithubConnected()) || user.getGithubAccessToken() == null) {
            return ResponseEntity.status(404).body("GitHub not connected");
        }
        // Do not expose the access token.
        return ResponseEntity.ok(
                java.util.Map.of(
                        "githubConnected", Boolean.TRUE.equals(user.getGithubConnected()),
                        "githubId", user.getGithubId(),
                        "githubUsername", user.getGithubUsername(),
                        "githubAvatarUrl", user.getGithubAvatarUrl(),
                        "githubTokenCreatedAt", user.getGithubTokenCreatedAt()
                )
        );
    }

    private static String generateState() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return org.apache.commons.codec.binary.Base64.encodeBase64URLSafeString(b);
    }
}

