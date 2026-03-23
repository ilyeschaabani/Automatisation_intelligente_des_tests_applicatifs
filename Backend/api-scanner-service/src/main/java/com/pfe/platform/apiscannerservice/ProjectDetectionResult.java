package com.pfe.platform.apiscannerservice;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.file.Path;

@Data
@AllArgsConstructor
public class ProjectDetectionResult {
    private Path projectRoot;
    private ProjectMetadata metadata;
}

