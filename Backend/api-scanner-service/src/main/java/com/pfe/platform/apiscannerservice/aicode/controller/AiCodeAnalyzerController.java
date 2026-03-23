package com.pfe.platform.apiscannerservice.aicode.controller;

import com.pfe.platform.apiscannerservice.aicode.model.AnalyzeRequest;
import com.pfe.platform.apiscannerservice.aicode.model.AnalyzeResponse;
import com.pfe.platform.apiscannerservice.aicode.service.AiCodeAnalyzerService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/analyze", produces = MediaType.APPLICATION_JSON_VALUE)
public class AiCodeAnalyzerController {

    private final AiCodeAnalyzerService service;

    public AiCodeAnalyzerController(AiCodeAnalyzerService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public AnalyzeResponse analyze(@Valid @RequestBody AnalyzeRequest request) {
        return service.analyzeRepo(request.repoUrl(), request.gitToken());
    }
}

