package com.pfe.platform.ms_gestion.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "project_members")
@IdClass(ProjectMemberId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMember {
    @Id
    private Long projectId;

    @Id
    private Long userId;

    @ManyToOne
    @MapsId("projectId")
    @JoinColumn(name = "project_id")
    private Project project;

    @Enumerated(EnumType.STRING)
    private Role role;

    public enum Role { ADMIN, TESTER, DEVOPS }
}
