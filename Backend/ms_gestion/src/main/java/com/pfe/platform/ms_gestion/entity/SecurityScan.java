package com.pfe.platform.ms_gestion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "security_scans")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
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
}
