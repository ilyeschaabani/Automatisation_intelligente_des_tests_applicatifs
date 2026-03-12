package com.pfe.platform.apiscannerservice.Model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional Git authentication information.
 * <p>
 * For GitHub/GitLab HTTPS cloning, prefer using {@code token} (Personal Access Token).
 * Username/password is supported for generic basic auth.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GitAuth {
    private String token;
    private String username;
    private String password;
}

