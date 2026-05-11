package com.pfe.platform.ms_gestion.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTestSuiteRequest {
    @NotBlank
    private String name;
    private String description;
    private String gitRepoUrl;
    private String gitBranch;
    private String modulePath;
    private String type; // WEB, API, UNIT, INTEGRATION
}
