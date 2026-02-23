package com.pfe.platform.testmanagementmicroservice.entity;

import com.pfe.platform.testmanagementmicroservice.entity.Enum.ProjectType;
import com.pfe.platform.testmanagementmicroservice.entity.Enum.RepoProvider;
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
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
     Long id;

    @Column(nullable = false)
     String name;

    @Column(length = 4000)
     String description;

     String repositoryUrl;

    @Enumerated(EnumType.STRING)
     ProjectType type;

     String defaultBranch;


    @Enumerated(EnumType.STRING)
    RepoProvider repoProvider;


    String createdBy;

    @Column(nullable = false)
    private boolean archived = false;



    @Column(nullable = false, updatable = false)
     Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (defaultBranch == null || defaultBranch.isBlank()) defaultBranch = "main";
    }

}

