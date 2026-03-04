package com.pfe.platform.testmanagementmicroservice.entity;

import com.pfe.platform.testmanagementmicroservice.entity.Enum.SessionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "test_campaigns")
public class TestCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    String name;

    String version;

    /** e.g. PLANNED, IN_PROGRESS, COMPLETED */
    String status;

    LocalDate startDate;

    LocalDate endDate;

    /** Optional: replaces session.environment */
    String environment;

    /** Optional: replaces session.triggerType (e.g. MANUAL, SCHEDULED, WEBHOOK, PIPELINE) */
    String triggerType;

    /** Replaces session.status */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    SessionStatus sessionStatus = SessionStatus.OPEN;

    /** Optional: replaces session.startDate */
    Instant executionStartDate;

    /** Optional: replaces session.endDate */
    Instant executionEndDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    Project project;

    String createdBy;

    @Column(nullable = false, updatable = false)
    Instant createdAt;

    @OneToOne(mappedBy = "campaign", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    TestReport report;

    @ManyToMany
    @JoinTable(
            name = "test_campaign_test_cases",
            joinColumns = @JoinColumn(name = "campaign_id"),
            inverseJoinColumns = @JoinColumn(name = "test_case_id")
    )
    Set<TestCase> testCases = new HashSet<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

}
