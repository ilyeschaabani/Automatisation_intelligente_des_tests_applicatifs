package com.pfe.platform.testmanagementmicroservice.entity;

import com.pfe.platform.testmanagementmicroservice.entity.Enum.ProjectType;
import com.pfe.platform.testmanagementmicroservice.entity.Enum.SourceType;
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
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
     Long id;

    @Column(nullable = false)
     String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
     ProjectType projectType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
     SourceType sourceType;

    @Column(length = 4000)
     String repositoryUrl;

    /** Reference to a secret/token stored elsewhere */
     String gitTokenId;

    /** e.g. "SPRING_BOOT", "NODE_JS", "DJANGO" ... */
     String technologyStack;

    @Column(nullable = false)
    private boolean deployed = false;

    @Column(nullable = false, updatable = false)
     Instant createdAt;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    List<TestSession> sessions = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

}
