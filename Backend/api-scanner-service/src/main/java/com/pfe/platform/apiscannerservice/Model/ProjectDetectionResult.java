package com.pfe.platform.apiscannerservice.Model;

import java.nio.file.Path;

public class ProjectDetectionResult {
    private final Path projectRoot;
    private final ProjectMetadata metadata;

    public ProjectDetectionResult(Path projectRoot, ProjectMetadata metadata) {
        this.projectRoot = projectRoot;
        this.metadata = metadata;
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public ProjectMetadata getMetadata() {
        return metadata;
    }
}

