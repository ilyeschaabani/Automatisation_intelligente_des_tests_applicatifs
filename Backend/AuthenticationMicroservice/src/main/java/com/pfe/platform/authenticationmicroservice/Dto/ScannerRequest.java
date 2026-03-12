package com.pfe.platform.authenticationmicroservice.Dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScannerRequest(
        String repoUrl,
        ScannerGitAuth gitAuth
) {
}

