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
@Table(name = "test_suites")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TestSuite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = true)
    private String gitRepoUrl;

    @Column(nullable = true)
    private String gitBranch;

    @Column(name = "module_path", nullable = true, length = 255)
    private String modulePath;

    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "suite", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TestCase> testCases = new ArrayList<>();
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private TestType type = TestType.WEB;

    public enum TestType { WEB, API, UNIT, INTEGRATION, UX_WEB, UX_MOBILE }
}
