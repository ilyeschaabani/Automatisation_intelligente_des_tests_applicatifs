package com.pfe.platform.msexecution.entity;


import jakarta.persistence.*;

@Entity
@Table(name = "test_cases")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suite_id", nullable = true)
    private TestSuite suite;

    // Read-only access to suite FK without triggering lazy loading
    @Column(name = "suite_id", insertable = false, updatable = false)
    private Long suiteId;
    
    @Enumerated(EnumType.STRING)
    private TestType type; // WEB, API

    @Column(name = "spring_profile")
    private String springProfile;

    private String databaseType;
    private String scriptPath;

    @Column(columnDefinition = "TEXT")
    private String generatedCode;
    @Column(nullable = false)
    private Boolean generated = false;

    private Boolean active = true;
    private Boolean flaky = false;
    private Integer maxDurationSeconds;

    @Column(columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String testData;

    public enum TestType { WEB, API, UNIT, INTEGRATION, FUNCTIONAL_WEB, FUNCTIONAL_MOBILE }

    public TestCase() {
    }

    public TestCase(Long id, TestType type, String scriptPath , String generatedCode , Boolean generated) {
        this.id = id;
        this.type = type;
        this.scriptPath = scriptPath;
        this.generatedCode = generatedCode;
        this.generated = generated;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TestSuite getSuite() {
        return suite;
    }

    public void setSuite(TestSuite suite) {
        this.suite = suite;
    }

    public Long getSuiteId() {
        return suiteId;
    }

    public TestType getType() {
        return type;
    }

    public void setType(TestType type) {
        this.type = type;
    }

    public String getSpringProfile() {
        return springProfile;
    }

    public void setSpringProfile(String springProfile) {
        this.springProfile = springProfile;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
    }

    public String getGeneratedCode() {
        return generatedCode;
    }

    public void setGeneratedCode(String generatedCode) {
        this.generatedCode = generatedCode;
    }
    public Boolean getGenerated() {
        return generated;
    }
    public void setGenerated(Boolean generated) { this.generated = generated; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Boolean getFlaky() { return flaky; }
    public void setFlaky(Boolean flaky) { this.flaky = flaky; }

    public Integer getMaxDurationSeconds() { return maxDurationSeconds; }
    public void setMaxDurationSeconds(Integer maxDurationSeconds) { this.maxDurationSeconds = maxDurationSeconds; }

    public String getTestData() { return testData; }
    public void setTestData(String testData) { this.testData = testData; }
}
