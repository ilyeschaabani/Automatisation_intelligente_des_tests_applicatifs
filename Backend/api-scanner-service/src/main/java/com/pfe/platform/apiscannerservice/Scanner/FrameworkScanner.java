package com.pfe.platform.apiscannerservice.Scanner;

import com.pfe.platform.apiscannerservice.Model.ApiContract;
import com.pfe.platform.apiscannerservice.Model.ProjectMetadata;

import java.nio.file.Path;

public interface FrameworkScanner {
    boolean supports(ProjectMetadata metadata);

    ApiContract scan(Path projectPath);
}
