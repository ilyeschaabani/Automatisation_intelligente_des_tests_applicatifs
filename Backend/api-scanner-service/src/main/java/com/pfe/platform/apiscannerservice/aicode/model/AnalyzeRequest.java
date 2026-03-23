package com.pfe.platform.apiscannerservice.aicode.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalyzeRequest(
        @NotBlank String repoUrl,
        String gitToken
) {
}

