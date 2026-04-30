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
    private Integer priority;
    private String riskLevel;
    private String scriptPath;
    private String testData;
    private String tags;
    private Integer maxDurationSeconds;
    private Boolean active;
    private Boolean flaky;
    private LocalDateTime createdAt;
}
