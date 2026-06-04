package com.pfe.platform.msexecution.entity;


import jakarta.persistence.*;

@Entity
@Table(name = "environments")
public class Environment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String baseUrlWeb;
    private String baseUrlApi;

    @Column(name = "git_repo_url")
    private String gitRepoUrl;

    @Column(name = "git_branch")
    private String gitBranch;

    @Column(name = "database_type")
    private String databaseType;

    public Environment() {
    }

    public Environment(Long id, String baseUrlWeb, String baseUrlApi,
                       String gitRepoUrl, String gitBranch, String databaseType) {
        this.id = id;
        this.baseUrlWeb = baseUrlWeb;
        this.baseUrlApi = baseUrlApi;
        this.gitRepoUrl = gitRepoUrl;
        this.gitBranch = gitBranch;
        this.databaseType = databaseType;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBaseUrlWeb() {
        return baseUrlWeb;
    }

    public void setBaseUrlWeb(String baseUrlWeb) {
        this.baseUrlWeb = baseUrlWeb;
    }

    public String getBaseUrlApi() {
        return baseUrlApi;
    }

    public void setBaseUrlApi(String baseUrlApi) {
        this.baseUrlApi = baseUrlApi;
    }

    public String getGitRepoUrl() { return gitRepoUrl; }
    public void setGitRepoUrl(String gitRepoUrl) { this.gitRepoUrl = gitRepoUrl; }

    public String getGitBranch() { return gitBranch; }
    public void setGitBranch(String gitBranch) { this.gitBranch = gitBranch; }

    public String getDatabaseType() { return databaseType; }
    public void setDatabaseType(String databaseType) { this.databaseType = databaseType; }
}
