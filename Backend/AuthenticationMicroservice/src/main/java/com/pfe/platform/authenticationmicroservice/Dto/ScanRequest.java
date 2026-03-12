package com.pfe.platform.authenticationmicroservice.Dto;

import jakarta.validation.constraints.NotBlank;

/** Request from frontend to start a scan for a Git repo URL. */
public record ScanRequest(
        @NotBlank String repoUrl
) {
}

