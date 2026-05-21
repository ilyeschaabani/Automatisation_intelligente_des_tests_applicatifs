package com.pfe.platform.msexecution.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "functional_evaluations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UxEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = true)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false)
    private Platform platform;

    @Column(name = "url")
    private String url;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "test_summary", columnDefinition = "text")
    private String testSummary;

    @Column(name = "ai_analysis", columnDefinition = "text")
    private String aiAnalysis;

    @Column(name = "page_content", columnDefinition = "text")
    private String pageContent;

    @Column(name = "logs", columnDefinition = "text")
    private String logs;

    @Column(name = "screenshot_url")
    private String screenshotUrl;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "generated_script", columnDefinition = "text")
    private String generatedScript;

    public enum Platform {
        WEB, MOBILE
    }

    public enum Status {
        PENDING, RUNNING, COMPLETED, FAILED
    }
}
