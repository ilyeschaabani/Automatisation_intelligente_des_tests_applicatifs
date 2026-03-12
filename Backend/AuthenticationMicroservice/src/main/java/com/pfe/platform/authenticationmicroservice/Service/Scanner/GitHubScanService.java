package com.pfe.platform.authenticationmicroservice.Service.Scanner;

import com.pfe.platform.authenticationmicroservice.Dto.ScanRequest;
import com.pfe.platform.authenticationmicroservice.Dto.ScannerGitAuth;
import com.pfe.platform.authenticationmicroservice.Dto.ScannerRequest;
import com.pfe.platform.authenticationmicroservice.Entity.User;
import com.pfe.platform.authenticationmicroservice.Service.Github.GitHubTokenManager;
import com.pfe.platform.authenticationmicroservice.Service.User.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
@RequiredArgsConstructor
public class GitHubScanService {

    private final UserService userService;
    private final GitHubTokenManager tokenManager;
    private final ApiScannerClient apiScannerClient;

    /**
     * @param userEmail authenticated platform user email
     * @return raw JSON response from scanner service
     */
    public String scanRepo(String userEmail, ScanRequest scanRequest) throws WebClientResponseException {
        User user = userService.getUserByEmail(userEmail);

        if (!Boolean.TRUE.equals(user.getGithubConnected()) || user.getGithubAccessToken() == null) {
            throw new GitHubNotConnectedException();
        }

        String storedToken = user.getGithubAccessToken();
        String accessToken;
        try {
            accessToken = tokenManager.decrypt(storedToken);
        } catch (IllegalStateException ex) {
            // wrong key / bad encrypted format -> force reconnect
            throw new GitHubNotConnectedException();
        }

        // Never log the token.
        ScannerRequest scannerRequest = new ScannerRequest(
                scanRequest.repoUrl(),
                new ScannerGitAuth(accessToken)
        );

        return apiScannerClient.scan(scannerRequest);
    }
}
