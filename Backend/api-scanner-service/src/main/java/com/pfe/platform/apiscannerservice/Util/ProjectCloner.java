package com.pfe.platform.apiscannerservice.Util;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ProjectCloner {

    public Path cloneToTemp(String repoUrl) {
        return cloneToTemp(repoUrl, null, null, null);
    }

    public Path cloneToTemp(String repoUrl, String gitToken, String gitUsername, String gitPassword) {
        try {
            Path dir = Files.createTempDirectory("api-scanner-");

            CloneCommand cmd = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(dir.toFile())
                    .setCloneAllBranches(false);

            CredentialsProvider credentials = buildCredentialsProvider(gitToken, gitUsername, gitPassword);
            if (credentials != null) {
                cmd.setCredentialsProvider(credentials);
            }

            try (Git ignored = cmd.call()) {
                return dir;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to clone repository: " + repoUrl, e);
        }
    }

    /**
     * For GitHub/GitLab HTTPS cloning:
     * - token: username can be any non-empty value, password is the token.
     */
    private CredentialsProvider buildCredentialsProvider(String gitToken, String gitUsername, String gitPassword) {
        if (gitToken != null && !gitToken.isBlank()) {
            return new UsernamePasswordCredentialsProvider("token", gitToken);
        }

        if (gitUsername != null && !gitUsername.isBlank() && gitPassword != null && !gitPassword.isBlank()) {
            return new UsernamePasswordCredentialsProvider(gitUsername, gitPassword);
        }

        return null;
    }

    public void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            if (!Files.exists(path)) return;
            try (var paths = Files.walk(path)) {
                paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (IOException ignored) {
        }
    }
}
