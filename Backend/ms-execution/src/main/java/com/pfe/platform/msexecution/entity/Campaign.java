package com.pfe.platform.msexecution.entity;


import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "campaigns")
public class Campaign {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "environment_id")
    private Long environmentId;

    @Enumerated(EnumType.STRING)
    private CampaignStatus status;

    @Enumerated(EnumType.STRING)
    private TriggerMode triggerMode;

    private String gitBranch;
    private String appVersion;
    private Integer progress = 0;
    private String currentStep;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public enum CampaignStatus { PENDING, RUNNING, FINISHED, FINISHED_WITH_ERRORS, ABORTED }
    public enum TriggerMode { MANUAL, SCHEDULED, CI }

    public Campaign() {
    }

    public Campaign(Long id, Long projectId, Long environmentId, CampaignStatus status, TriggerMode triggerMode,
                    String gitBranch, Integer progress, String currentStep, LocalDateTime startedAt,
                    LocalDateTime finishedAt) {
        this.id = id;
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.status = status;
        this.triggerMode = triggerMode;
        this.gitBranch = gitBranch;
        this.progress = progress;
        this.currentStep = currentStep;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
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

    public CampaignStatus getStatus() {
        return status;
    }

    public void setStatus(CampaignStatus status) {
        this.status = status;
    }

    public TriggerMode getTriggerMode() {
        return triggerMode;
    }

    public void setTriggerMode(TriggerMode triggerMode) {
        this.triggerMode = triggerMode;
    }

    public String getGitBranch() { return gitBranch; }
    public void setGitBranch(String gitBranch) { this.gitBranch = gitBranch; }

    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }

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
}
