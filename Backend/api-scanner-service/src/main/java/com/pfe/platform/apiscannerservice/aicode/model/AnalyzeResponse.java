package com.pfe.platform.apiscannerservice.aicode.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalyzeResponse(
        String framework,
        Confidence confidence,
        List<String> evidence
) {
    public enum Confidence { HIGH, MEDIUM, LOW }
}

