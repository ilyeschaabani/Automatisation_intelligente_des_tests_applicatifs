package com.pfe.platform.testmanagementmicroservice.entity;

import com.pfe.platform.testmanagementmicroservice.entity.Enum.ExecutionStatus;
import com.pfe.platform.testmanagementmicroservice.entity.Enum.ExecutionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "test_executions")
public class TestExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    Integer executionNumber;

    Instant executionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ExecutionType executionType = ExecutionType.INITIAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ExecutionStatus status = ExecutionStatus.QUEUED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    TestCampaign campaign;

    @PrePersist
    void onCreate() {
        if (executionDate == null) executionDate = Instant.now();
    }
}
