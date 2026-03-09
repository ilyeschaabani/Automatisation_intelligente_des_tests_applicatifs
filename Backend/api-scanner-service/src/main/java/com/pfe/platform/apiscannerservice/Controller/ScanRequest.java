package com.pfe.platform.apiscannerservice.Controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScanRequest {
    private String projectPath;
    private String repoUrl;

    /**
     * Optional GitHub/GitLab Personal Access Token for cloning HTTPS repos.
     * Prefer this over username/password.
     */
    private String gitToken;

    /** Optional basic auth username for HTTPS cloning. */
    private String gitUsername;

    /** Optional basic auth password for HTTPS cloning (or PAT if provider expects it here). */
    private String gitPassword;
}
