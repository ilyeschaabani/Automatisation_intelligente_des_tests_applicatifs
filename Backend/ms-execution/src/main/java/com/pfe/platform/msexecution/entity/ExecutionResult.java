package com.pfe.platform.msexecution.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "execution_results")
public class ExecutionResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long campaignId;
    private Long testCaseId;

    @Enumerated(EnumType.STRING)
    private ResultStatus status;

    private Long durationMs;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String logs;

    private String screenshotUrl;
    private LocalDateTime executedAt = LocalDateTime.now();

    public enum ResultStatus { SUCCESS, FAILURE, ERROR }

    public ExecutionResult() {
    }

    public ExecutionResult(Long id, Long campaignId, Long testCaseId, ResultStatus status, Long durationMs,
                           String errorMessage, String logs, String screenshotUrl, LocalDateTime executedAt) {
        this.id = id;
        this.campaignId = campaignId;
        this.testCaseId = testCaseId;
        this.status = status;
        this.durationMs = durationMs;
        this.errorMessage = errorMessage;
        this.logs = logs;
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

    public ResultStatus getStatus() {
        return status;
    }

    public void setStatus(ResultStatus status) {
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

    public String getScreenshotUrl() {
        return screenshotUrl;
    }

    public void setScreenshotUrl(String screenshotUrl) {
        this.screenshotUrl = screenshotUrl;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }
}
