package com.pfe.platform.apiscannerservice.Service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Optional default Git authentication.
 *
 * <p>These defaults are used only when the API request doesn't provide credentials.
 * Prefer providing a token per request for private repos.
 */
@Data
@Component
@ConfigurationProperties(prefix = "scanner.git")
public class GitAuthProperties {
    /** GitHub/GitLab Personal Access Token (PAT). */
    private String token;

    /** Optional basic auth username. */
    private String username;

    /** Optional basic auth password. */
    private String password;
}
