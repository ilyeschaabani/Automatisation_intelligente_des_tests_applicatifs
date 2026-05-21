package com.pfe.platform.msexecution.dto;

import com.pfe.platform.msexecution.entity.UxEvaluation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UxEvaluationDto {
    private Long id;
    private Long projectId;
    private UxEvaluation.Platform platform;
    private String url;
    private String description;
    private UxEvaluation.Status status;
    private String testSummary;
    private String aiAnalysis;
    private String pageContent;
    private String logs;
    private String screenshotUrl;
    private String generatedScript;
    private Long durationMs;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime executedAt;

    public static UxEvaluationDto fromEntity(UxEvaluation e) {
        return UxEvaluationDto.builder()
                .id(e.getId())
                .projectId(e.getProjectId())
                .platform(e.getPlatform())
                .url(e.getUrl())
                .description(e.getDescription())
                .status(e.getStatus())
                .testSummary(e.getTestSummary())
                .aiAnalysis(e.getAiAnalysis())
                .pageContent(e.getPageContent())
                .logs(e.getLogs())
                .screenshotUrl(e.getScreenshotUrl())
                .generatedScript(e.getGeneratedScript())
                .durationMs(e.getDurationMs())
                .errorMessage(e.getErrorMessage())
                .createdAt(e.getCreatedAt())
                .executedAt(e.getExecutedAt())
                .build();
    }
}
