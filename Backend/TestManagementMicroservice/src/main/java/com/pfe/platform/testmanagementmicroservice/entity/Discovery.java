package com.pfe.platform.testmanagementmicroservice.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "discoveries")
public class Discovery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    Project project;

    @Column(nullable = false)
    String repoUrl;

    String branch;

    @Column(nullable = false)
    String status; // queued, running, done, error

    @Column(length = 4000)
    String error; // if discovery failed

    @Column(updatable = false)
    Instant createdAt;

    @OneToMany(mappedBy = "discovery", cascade = CascadeType.ALL, orphanRemoval = true)
    List<Endpoint> endpoints = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}


