package com.pfe.platform.authenticationmicroservice.Controller;

import com.pfe.platform.authenticationmicroservice.Dto.ScanRequest;
import com.pfe.platform.authenticationmicroservice.Service.Scanner.GitHubNotConnectedException;
import com.pfe.platform.authenticationmicroservice.Service.Scanner.GitHubScanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GitHubScanController {

    private final GitHubScanService scanService;

    @PostMapping(value = "/scan", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> scan(@AuthenticationPrincipal UserDetails userDetails,
                                  @Valid @RequestBody ScanRequest req) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            String body = scanService.scanRepo(userDetails.getUsername(), req);
            // Pass-through JSON from scanner service
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
        } catch (GitHubNotConnectedException ex) {
            return ResponseEntity.status(404).body("GitHub not connected");
        } catch (WebClientResponseException ex) {
            // Propagate status code + body from scanner service.
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
    }
}

