package com.pfe.platform.testmanagementmicroservice.entity;

import com.pfe.platform.testmanagementmicroservice.entity.Enum.TestType;
import com.pfe.platform.testmanagementmicroservice.entity.Enum.ToolTest;
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

    @Column(length = 4000)
    String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    TestType testType;

    /** e.g. LOW, MEDIUM, HIGH (kept as String for now) */
    String priority;

    @Enumerated(EnumType.STRING)
    ToolTest tool;

    Double riskScore;

    // Removed campaign relation: campaigns link to test cases via join table.
}
