package com.pfe.platform.msexecution.entity;


import jakarta.persistence.*;

@Entity
@Table(name = "test_cases")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private TestType type; // WEB, API
    private String scriptPath;

    public enum TestType { WEB, API, UNIT, INTEGRATION }

    public TestCase() {
    }

    public TestCase(Long id, TestType type, String scriptPath) {
        this.id = id;
        this.type = type;
        this.scriptPath = scriptPath;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TestType getType() {
        return type;
    }

    public void setType(TestType type) {
        this.type = type;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
    }
}
