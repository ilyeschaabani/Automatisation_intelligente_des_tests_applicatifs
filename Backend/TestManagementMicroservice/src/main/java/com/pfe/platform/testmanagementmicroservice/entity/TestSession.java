package com.pfe.platform.testmanagementmicroservice.entity;

import com.pfe.platform.testmanagementmicroservice.entity.Enum.SessionStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "test_sessions")
public class TestSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    Instant startDate;

    Instant endDate;

    String environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    SessionStatus status = SessionStatus.OPEN;

    /** e.g. MANUAL, SCHEDULED, WEBHOOK, PIPELINE */
    String triggerType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    Project project;

    /** Optional link to campaign for grouping */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    TestCampaign campaign;

    @OneToOne(mappedBy = "session", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    TestReport report;

    @PrePersist
    void onCreate() {
        if (startDate == null) startDate = Instant.now();
    }
}

