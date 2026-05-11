package com.pfe.platform.msexecution.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "test_suites")
public class TestSuite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private String gitRepoUrl;
    private String gitBranch;

    public TestSuite() {
    }

    public TestSuite(Long id, String name, String gitRepoUrl, String gitBranch) {
        this.id = id;
        this.name = name;
        this.gitRepoUrl = gitRepoUrl;
        this.gitBranch = gitBranch;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGitRepoUrl() {
        return gitRepoUrl;
    }

    public void setGitRepoUrl(String gitRepoUrl) {
        this.gitRepoUrl = gitRepoUrl;
    }

    public String getGitBranch() {
        return gitBranch;
    }

    public void setGitBranch(String gitBranch) {
        this.gitBranch = gitBranch;
    }
}
