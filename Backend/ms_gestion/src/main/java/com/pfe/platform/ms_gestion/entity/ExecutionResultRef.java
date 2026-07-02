package com.pfe.platform.ms_gestion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "execution_results")
@Getter @Setter @NoArgsConstructor
public class ExecutionResultRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long campaignId;
    private Long testCaseId;
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "assigned_to_user_id")
    private Long assignedToUserId;
}
