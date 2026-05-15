package com.pfe.platform.ms_gestion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Campaign {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environment;

    @Column(nullable = false)
    private String name;

    private String appVersion;

    private String gitBranch;

    @Enumerated(EnumType.STRING)
    private TriggerMode triggerMode;

    @Enumerated(EnumType.STRING)
    private CampaignStatus status = CampaignStatus.PENDING;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CampaignTestCase> campaignTestCases = new ArrayList<>();

    public enum TriggerMode { MANUAL, SCHEDULED, CI }
    public enum CampaignStatus { PENDING, RUNNING, FINISHED, FINISHED_WITH_ERRORS, ABORTED }
}