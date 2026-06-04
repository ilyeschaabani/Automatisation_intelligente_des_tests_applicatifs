package com.pfe.platform.msexecution.dto;

import com.pfe.platform.msexecution.entity.ExecutionResult;

import java.time.LocalDateTime;

public class ExecutionResultDto {
    private Long id;
    private Long campaignId;
    private Long testCaseId;
    private String testType;
    private String status;
    private Long durationMs;
    private String errorMessage;
    private String logs;
    private String aiAnalysis;
    private String uxAnalysis;
    private String screenshotUrl;
    private String testMethodResults; // JSON array of per-method Surefire results
    private LocalDateTime executedAt;

    public ExecutionResultDto() {
    }

    public ExecutionResultDto(Long id, Long campaignId, Long testCaseId, String testType, String status, Long durationMs,
                              String errorMessage, String logs, String aiAnalysis, String uxAnalysis, String screenshotUrl, LocalDateTime executedAt) {
        this.id = id;
        this.campaignId = campaignId;
        this.testCaseId = testCaseId;
        this.testType = testType;
        this.status = status;
        this.durationMs = durationMs;
        this.errorMessage = errorMessage;
        this.logs = logs;
        this.aiAnalysis = aiAnalysis;
        this.uxAnalysis = uxAnalysis;
        this.screenshotUrl = screenshotUrl;
        this.executedAt = executedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(Long campaignId) {
        this.campaignId = campaignId;
    }

    public Long getTestCaseId() {
        return testCaseId;
    }

    public void setTestCaseId(Long testCaseId) {
        this.testCaseId = testCaseId;
    }

    public String getTestType() {
        return testType;
    }

    public void setTestType(String testType) {
        this.testType = testType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getLogs() {
        return logs;
    }

    public void setLogs(String logs) {
        this.logs = logs;
    }

    public String getAiAnalysis() {
        return aiAnalysis;
    }

    public void setAiAnalysis(String aiAnalysis) {
        this.aiAnalysis = aiAnalysis;
    }

    public String getUxAnalysis() {
        return uxAnalysis;
    }

    public void setUxAnalysis(String uxAnalysis) {
        this.uxAnalysis = uxAnalysis;
    }

    public String getScreenshotUrl() {
        return screenshotUrl;
    }

    public void setScreenshotUrl(String screenshotUrl) {
        this.screenshotUrl = screenshotUrl;
    }

    public String getTestMethodResults() { return testMethodResults; }
    public void setTestMethodResults(String testMethodResults) { this.testMethodResults = testMethodResults; }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    public static ExecutionResultDto fromEntity(ExecutionResult entity) {
        ExecutionResultDto dto = new ExecutionResultDto(
                entity.getId(),
                entity.getCampaignId(),
                entity.getTestCaseId(),
                entity.getTestType() != null ? entity.getTestType().name() : null,
                entity.getStatus() != null ? entity.getStatus().name() : null,
                entity.getDurationMs(),
                entity.getErrorMessage(),
                entity.getLogs(),
                entity.getAiAnalysis(),
                entity.getUxAnalysis(),
                entity.getScreenshotUrl(),
                entity.getExecutedAt()
        );
        dto.setTestMethodResults(entity.getTestMethodResults());
        return dto;
    }
}
