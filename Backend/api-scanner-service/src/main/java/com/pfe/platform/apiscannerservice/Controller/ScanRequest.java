package com.pfe.platform.apiscannerservice.Controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pfe.platform.apiscannerservice.Model.GitAuth;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScanRequest {
    private String projectPath;
    private String repoUrl;

    /**
     * Optional Git auth block (preferred): {"gitAuth": {"token": "..."}}
     */
    private GitAuth gitAuth;

    /**
     * Optional GitHub/GitLab Personal Access Token for cloning HTTPS repos.
     * Prefer this over username/password.
     *
     * @deprecated Use {@link #gitAuth} instead.
     */
    @Deprecated
    private String gitToken;

    /** Optional basic auth username for HTTPS cloning.
     * @deprecated Use {@link #gitAuth} instead.
     */
    @Deprecated
    private String gitUsername;

    /** Optional basic auth password for HTTPS cloning (or PAT if provider expects it here).
     * @deprecated Use {@link #gitAuth} instead.
     */
    @Deprecated
    private String gitPassword;
}
