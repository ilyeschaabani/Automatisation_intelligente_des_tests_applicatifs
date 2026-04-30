package com.pfe.platform.ms_gestion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "test_cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TestCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suite_id", nullable = false)
    private TestSuite suite;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private TestType type; // WEB, API

    private Integer priority; // 1..5

    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel; // CRITICAL, HIGH, MEDIUM, LOW

    private String scriptPath;

    @Column(columnDefinition = "JSONB")
    private String testData; // JSON

    private String tags;

    private Integer maxDurationSeconds;

    private Boolean active = true;

    private Boolean flaky = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum TestType { WEB, API }
    public enum RiskLevel { CRITICAL, HIGH, MEDIUM, LOW }
}
