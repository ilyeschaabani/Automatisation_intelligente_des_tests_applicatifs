package com.pfe.platform.ms_gestion.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TestCaseResponse {
    private Long id;
    private Long suiteId;
    private String title;
    private String description;
    private String type;
    private String springProfile;
    private Integer priority;
    private String riskLevel;
    private String gitRepoUrl;
    private String scriptPath;
    private String testData;
    private String tags;
    private Integer maxDurationSeconds;
    private Boolean active;
    private Boolean flaky;
    private LocalDateTime createdAt;
    private String generatedCode;
    private Boolean generated;
    private String databaseType;
    private String targetClassName;
}
