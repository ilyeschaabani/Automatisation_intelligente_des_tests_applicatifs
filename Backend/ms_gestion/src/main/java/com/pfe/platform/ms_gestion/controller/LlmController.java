package com.pfe.platform.ms_gestion.controller;

import com.pfe.platform.ms_gestion.dto.request.GenerateTestRequest;
import com.pfe.platform.ms_gestion.dto.response.GenerateTestResponse;
import com.pfe.platform.ms_gestion.service.LlmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/llm")
public class LlmController {

    private final LlmService llmService;

    @PostMapping("/generate-test")
    public ResponseEntity<GenerateTestResponse> generateTest(@RequestBody GenerateTestRequest request) {
        String code = llmService.generateTestCode(request.getType(), request.getDescription());
        GenerateTestResponse resp = new GenerateTestResponse();
        resp.setGeneratedCode(code);
        return ResponseEntity.ok(resp);
    }
}
