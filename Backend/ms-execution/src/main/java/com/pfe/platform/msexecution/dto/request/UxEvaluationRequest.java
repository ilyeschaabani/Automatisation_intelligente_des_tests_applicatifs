package com.pfe.platform.msexecution.dto.request;

public class UxEvaluationRequest {
    private Long projectId;
    private String platform;
    private String url;
    private String description;
    private String generatedScript;
    private String apkPath;
    private String reviewMode;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getGeneratedScript() { return generatedScript; }
    public void setGeneratedScript(String generatedScript) { this.generatedScript = generatedScript; }
    public String getApkPath() { return apkPath; }
    public void setApkPath(String apkPath) { this.apkPath = apkPath; }
    public String getReviewMode() { return reviewMode; }
    public void setReviewMode(String reviewMode) { this.reviewMode = reviewMode; }
}
