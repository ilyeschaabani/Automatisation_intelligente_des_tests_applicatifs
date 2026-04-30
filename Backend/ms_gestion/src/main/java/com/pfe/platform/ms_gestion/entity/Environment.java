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

    @Column(columnDefinition = "JSONB")
    private String variables; // stockage JSON sous forme de String

    private LocalDateTime createdAt = LocalDateTime.now();
}
