package com.pfe.platform.ms_gestion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    private TestType type;

    private String springProfile;
    private String databaseType;

    private Integer priority;

    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel; // CRITICAL, HIGH, MEDIUM, LOW

    @Column(name = "git_repo_url")
    private String gitRepoUrl;

    private String scriptPath;

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String testData;

    private String tags;

    private Integer maxDurationSeconds;

    private Boolean active = true;
    @Column(columnDefinition = "TEXT")
    private String generatedCode;

    @Column(nullable = false)
    private Boolean generated = false;

    private Boolean flaky = false;

    @Column(name = "target_class_name")
    private String targetClassName;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum TestType { WEB, API, UNIT, INTEGRATION, UX_WEB, UX_MOBILE }
    public enum RiskLevel { CRITICAL, HIGH, MEDIUM, LOW }
}
