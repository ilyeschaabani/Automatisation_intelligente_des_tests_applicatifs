package com.pfe.platform.ms_gestion.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateTestCaseRequest {
    @NotBlank
    private String title;
    private String description;
    @NotNull
    private String type; // WEB, API, UNIT, INTEGRATION
    private String springProfile;
    private Integer priority;
    private String riskLevel; // CRITICAL, HIGH, MEDIUM, LOW
    private String scriptPath;
    private String testData; // JSON
    private String tags;
    private Integer maxDurationSeconds;
    private String generatedCode;      // optionnel, rempli par l'IA
    private Boolean useAI = false;     // si true, on génère
    private String descriptionAI;      // description en langage naturel
}
