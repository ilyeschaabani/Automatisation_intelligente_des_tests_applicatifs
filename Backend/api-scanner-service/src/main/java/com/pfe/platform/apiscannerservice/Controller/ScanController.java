package com.pfe.platform.apiscannerservice.Controller;

import com.pfe.platform.apiscannerservice.Model.ApiContract;
import com.pfe.platform.apiscannerservice.Model.GitAuth;
import com.pfe.platform.apiscannerservice.Service.ScannerEngine;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/scan", produces = MediaType.APPLICATION_JSON_VALUE)
public class ScanController {

    private final ScannerEngine engine;

    public ScanController(ScannerEngine engine) {
        this.engine = engine;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiContract scan(@RequestBody ScanRequest request) {
        String projectPath = request != null ? request.getProjectPath() : null;
        String repoUrl = request != null ? request.getRepoUrl() : null;

        GitAuth auth = request != null ? request.getGitAuth() : null;

        String gitToken = auth != null ? auth.getToken() : (request != null ? request.getGitToken() : null);
        String gitUsername = auth != null ? auth.getUsername() : (request != null ? request.getGitUsername() : null);
        String gitPassword = auth != null ? auth.getPassword() : (request != null ? request.getGitPassword() : null);

        return engine.scan(projectPath, repoUrl, gitToken, gitUsername, gitPassword);
    }
}
