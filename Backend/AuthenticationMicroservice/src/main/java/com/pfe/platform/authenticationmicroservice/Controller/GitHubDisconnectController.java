package com.pfe.platform.authenticationmicroservice.Controller;

import com.pfe.platform.authenticationmicroservice.Entity.User;
import com.pfe.platform.authenticationmicroservice.Repository.UserRepository;
import com.pfe.platform.authenticationmicroservice.Service.User.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GitHubDisconnectController {

    private final UserService userService;
    private final UserRepository userRepository;

    @PostMapping("/disconnect")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userService.getUserByEmail(userDetails.getUsername());

        // Always succeed regardless of current token validity.
        user.setGithubConnected(Boolean.FALSE);
        user.setGithubAccessToken(null);
        user.setGithubId(null);
        user.setGithubUsername(null);
        user.setGithubAvatarUrl(null);
        user.setGithubTokenCreatedAt((Instant) null);

        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }
}

