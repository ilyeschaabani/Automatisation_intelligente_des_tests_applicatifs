package com.pfe.platform.apiscannerservice;

import lombok.Data;

/**
 * Test-facing properties holder.
 *
 * The main scanner module also exposes a Spring Boot @ConfigurationProperties version under another package.
 */
@Data
public class GitAuthProperties {
    private String token;
    private String username;
    private String password;
}

