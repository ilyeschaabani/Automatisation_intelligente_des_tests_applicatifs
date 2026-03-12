package com.pfe.platform.authenticationmicroservice.Controller;

import com.pfe.platform.authenticationmicroservice.Entity.User;
import com.pfe.platform.authenticationmicroservice.Repository.UserRepository;
import com.pfe.platform.authenticationmicroservice.Service.Github.GitHubTokenManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "eureka.client.enabled=false"
})
@AutoConfigureMockMvc
class GitHubScanControllerTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GitHubTokenManager tokenManager;

    @MockBean
    com.pfe.platform.authenticationmicroservice.Service.Scanner.ApiScannerClient apiScannerClient;

    private static final String USER_EMAIL = "user@example.com";

    @BeforeEach
    void setup() {
        userRepository.deleteAll();
        User u = new User();
        u.setEmail(USER_EMAIL);
        u.setPassword("pw");
        u.setGithubConnected(Boolean.FALSE);
        u.setGithubAccessToken(null);
        userRepository.save(u);
    }

    @Test
    void scan_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/github/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/a/b\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scan_githubNotConnected_returns404() throws Exception {
        mockMvc.perform(post("/api/github/scan")
                        .with(user(USER_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/a/b\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("GitHub not connected"));
    }

    @Test
    void scan_scannerReturns200_passThroughBody() throws Exception {
        User u = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        u.setGithubConnected(Boolean.TRUE);
        u.setGithubAccessToken(tokenManager.encrypt("gho_token"));
        userRepository.save(u);

        when(apiScannerClient.scan(any())).thenReturn("{\"ok\":true}");

        mockMvc.perform(post("/api/github/scan")
                        .with(user(USER_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/a/b\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"ok\":true}"));
    }

    @Test
    void scan_scannerReturns500_propagatesStatusAndBody() throws Exception {
        User u = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        u.setGithubConnected(Boolean.TRUE);
        u.setGithubAccessToken(tokenManager.encrypt("gho_token"));
        userRepository.save(u);

        WebClientResponseException ex = WebClientResponseException.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                HttpHeaders.EMPTY,
                "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(apiScannerClient.scan(any())).thenThrow(ex);

        mockMvc.perform(post("/api/github/scan")
                        .with(user(USER_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/a/b\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"error\":\"boom\"}"));
    }
}
