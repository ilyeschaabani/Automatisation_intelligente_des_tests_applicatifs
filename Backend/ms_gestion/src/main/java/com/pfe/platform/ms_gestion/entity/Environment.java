package com.pfe.platform.ms_gestion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "environments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class Environment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String name;

    private String baseUrlWeb;
    private String baseUrlApi;

    @Column(name = "git_repo_url")
    private String gitRepoUrl;

    @Column(name = "git_branch")
    private String gitBranch;

    @Column(name = "database_type")
    private String databaseType; // POSTGRESQL | MYSQL | H2 | MONGODB

    private LocalDateTime createdAt = LocalDateTime.now();
}
