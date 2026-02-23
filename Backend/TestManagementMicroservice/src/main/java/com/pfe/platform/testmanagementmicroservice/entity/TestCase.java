package com.pfe.platform.testmanagementmicroservice.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "test_cases")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    String name;

    /** e.g. FUNCTIONAL, REGRESSION, PERFORMANCE */
    String type;

    /** e.g. LOW, MEDIUM, HIGH */
    String priority;

    Double riskScore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suite_id", nullable = false)
    TestSuite suite;


}

