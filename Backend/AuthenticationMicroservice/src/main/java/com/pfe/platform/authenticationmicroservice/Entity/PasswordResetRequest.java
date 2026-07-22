package com.pfe.platform.authenticationmicroservice.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "password_reset_requests")
public class PasswordResetRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResetStatus status = ResetStatus.PENDING;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant processedAt;

    private Long processedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public enum ResetStatus {
        PENDING, APPROVED, REJECTED
    }
}
