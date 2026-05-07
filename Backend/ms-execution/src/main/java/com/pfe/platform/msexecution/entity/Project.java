package com.pfe.platform.msexecution.entity;


import jakarta.persistence.*;

@Entity
@Table(name = "projects")
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String gitRepoUrl;
    private String gitDefaultBranch;

    public Project() {
    }

    public Project(Long id, String gitRepoUrl, String gitDefaultBranch) {
        this.id = id;
        this.gitRepoUrl = gitRepoUrl;
        this.gitDefaultBranch = gitDefaultBranch;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGitRepoUrl() {
        return gitRepoUrl;
    }

    public void setGitRepoUrl(String gitRepoUrl) {
        this.gitRepoUrl = gitRepoUrl;
    }

    public String getGitDefaultBranch() {
        return gitDefaultBranch;
    }

    public void setGitDefaultBranch(String gitDefaultBranch) {
        this.gitDefaultBranch = gitDefaultBranch;
    }
}
