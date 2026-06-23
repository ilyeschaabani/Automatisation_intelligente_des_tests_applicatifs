package com.pfe.platform.msexecution.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Résultat persisté d'un cas de test fonctionnel — permet un rapport répétable
 * et la comparaison entre exécutions (détection de régressions).
 */
@Entity
@Table(name = "functional_test_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FunctionalTestResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_id", nullable = false)
    private Long evaluationId;

    @Column(name = "form_label", columnDefinition = "text")
    private String formLabel;

    @Column(name = "scenario")
    private String scenario;

    @Column(name = "target_field", columnDefinition = "text")
    private String targetField;

    @Column(name = "input_data", columnDefinition = "text")
    private String inputData;

    @Column(name = "expected", columnDefinition = "text")
    private String expected;

    @Column(name = "observed", columnDefinition = "text")
    private String observed;

    @Column(name = "status")
    private String status;

    @Column(name = "severity")
    private String severity;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "evidence", columnDefinition = "text")
    private String evidence;

    @Column(name = "human_validated")
    private Boolean humanValidated;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
