package com.pfe.platform.msexecution.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "evaluation_members", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"evaluation_id", "user_id"})
})
public class EvaluationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_id", nullable = false)
    private Long evaluationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    public enum Role {
        OWNER,
        TESTER,
        VIEWER
    }

    public EvaluationMember() {}

    public EvaluationMember(Long evaluationId, Long userId, Role role) {
        this.evaluationId = evaluationId;
        this.userId = userId;
        this.role = role;
        this.assignedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEvaluationId() { return evaluationId; }
    public void setEvaluationId(Long evaluationId) { this.evaluationId = evaluationId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
}
