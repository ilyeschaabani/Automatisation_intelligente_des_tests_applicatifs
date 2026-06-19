package com.pfe.platform.msexecution.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "security_scans")
public class SecurityScan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String scanRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanType scanType;

    @Column(nullable = false)
    private String engine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanStatus status;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    private String duration;
    private Integer linesAnalyzed;
    private Integer filesAnalyzed;
    private Double coverage;
    private String branch;
    private String commitHash;
    private String targetUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    public enum ScanType { SAST, DAST, SCA }
    public enum ScanStatus { RUNNING, COMPLETED, FAILED, SCHEDULED }

    public SecurityScan() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getScanRef() { return scanRef; }
    public void setScanRef(String scanRef) { this.scanRef = scanRef; }

    public ScanType getScanType() { return scanType; }
    public void setScanType(ScanType scanType) { this.scanType = scanType; }

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }

    public ScanStatus getStatus() { return status; }
    public void setStatus(ScanStatus status) { this.status = status; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public Integer getLinesAnalyzed() { return linesAnalyzed; }
    public void setLinesAnalyzed(Integer linesAnalyzed) { this.linesAnalyzed = linesAnalyzed; }

    public Integer getFilesAnalyzed() { return filesAnalyzed; }
    public void setFilesAnalyzed(Integer filesAnalyzed) { this.filesAnalyzed = filesAnalyzed; }

    public Double getCoverage() { return coverage; }
    public void setCoverage(Double coverage) { this.coverage = coverage; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getCommitHash() { return commitHash; }
    public void setCommitHash(String commitHash) { this.commitHash = commitHash; }

    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
}
