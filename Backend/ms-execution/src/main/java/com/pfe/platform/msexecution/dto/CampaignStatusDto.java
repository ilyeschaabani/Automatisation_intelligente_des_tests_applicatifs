package com.pfe.platform.msexecution.dto;

import com.pfe.platform.msexecution.entity.Campaign;

import java.time.LocalDateTime;
import java.util.List;

public class CampaignStatusDto {
    private Long id;
    private Long projectId;
    private Long environmentId;
    private String status; // PENDING, RUNNING, FINISHED, FINISHED_WITH_ERRORS
    private Integer progress;
    private String currentStep;
    private String triggerMode;
    private String gitBranch;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private List<ExecutionResultDto> executionResults;

    public CampaignStatusDto() {
    }

    public CampaignStatusDto(Long id, Long projectId, Long environmentId, String status, Integer progress,
                             String currentStep, String triggerMode, String gitBranch, LocalDateTime startedAt,
                             LocalDateTime finishedAt, List<ExecutionResultDto> executionResults) {
        this.id = id;
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.status = status;
        this.progress = progress;
        this.currentStep = currentStep;
        this.triggerMode = triggerMode;
        this.gitBranch = gitBranch;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.executionResults = executionResults;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(Long environmentId) {
        this.environmentId = environmentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }

    public String getTriggerMode() {
        return triggerMode;
    }

    public void setTriggerMode(String triggerMode) {
        this.triggerMode = triggerMode;
    }

    public String getGitBranch() {
        return gitBranch;
    }

    public void setGitBranch(String gitBranch) {
        this.gitBranch = gitBranch;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public List<ExecutionResultDto> getExecutionResults() {
        return executionResults;
    }

    public void setExecutionResults(List<ExecutionResultDto> executionResults) {
        this.executionResults = executionResults;
    }

    public static CampaignStatusDto fromEntity(Campaign campaign, List<ExecutionResultDto> results) {
        return new CampaignStatusDto(
                campaign.getId(),
                campaign.getProjectId(),
                campaign.getEnvironmentId(),
                campaign.getStatus() != null ? campaign.getStatus().name() : null,
                campaign.getProgress(),
                campaign.getCurrentStep(),
                campaign.getTriggerMode() != null ? campaign.getTriggerMode().name() : null,
                campaign.getGitBranch(),
                campaign.getStartedAt(),
                campaign.getFinishedAt(),
                results
        );
    }
}
