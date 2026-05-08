package com.pfe.platform.ms_gestion.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseWithStatusResponse {
    private Long id;
    private String title;
    private String description;
    private String type;
    private Integer priority;
    private String riskLevel;
    private String scriptPath;
    private String tags;
    private Integer maxDurationSeconds;
    private Boolean active;
    private Boolean flaky;
    private LocalDateTime createdAt;
    
    // Execution status fields
    private String executionStatus; // QUEUED, RUNNING, SUCCESS, FAILURE, ERROR, or null if not executed
    private Long executionDurationMs;
    private LocalDateTime lastExecutedAt;
    private String lastErrorMessage;
}
