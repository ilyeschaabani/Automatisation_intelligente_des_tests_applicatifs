package com.pfe.platform.msexecution.controller;

import com.pfe.platform.msexecution.dto.UxEvaluationDto;
import com.pfe.platform.msexecution.dto.request.UxEvaluationRequest;
import com.pfe.platform.msexecution.entity.UxEvaluation;
import com.pfe.platform.msexecution.repository.UxEvaluationRepository;
import com.pfe.platform.msexecution.service.UxEvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.nio.file.Path;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/functional-evaluation", "/api/intelligence/ux-evaluations"})
@RequiredArgsConstructor
public class UxEvaluationController {

    private final UxEvaluationRepository uxEvaluationRepository;
    private final UxEvaluationService uxEvaluationService;

    @PostMapping
    public ResponseEntity<UxEvaluationDto> create(@Valid @RequestBody UxEvaluationRequest req) {
        UxEvaluation created = uxEvaluationService.createEvaluation(req);
        return ResponseEntity.ok(UxEvaluationDto.fromEntity(created));
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<Void> execute(@PathVariable Long id) {
        uxEvaluationService.executeEvaluation(id);
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    public ResponseEntity<List<UxEvaluationDto>> list(@RequestParam(required = false) Long projectId,
                                                      @RequestParam(required = false) String platform) {
        List<UxEvaluation> list;
        if (projectId == null) {
            if (platform == null || platform.isBlank()) {
                list = uxEvaluationRepository.findAllByOrderByCreatedAtDesc();
            } else {
                UxEvaluation.Platform p = UxEvaluation.Platform.valueOf(platform.toUpperCase());
                list = uxEvaluationRepository.findByPlatformOrderByCreatedAtDesc(p);
            }
        } else {
            if (platform == null || platform.isBlank()) {
                list = uxEvaluationRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
            } else {
                UxEvaluation.Platform p = UxEvaluation.Platform.valueOf(platform.toUpperCase());
                list = uxEvaluationRepository.findByProjectIdAndPlatformOrderByCreatedAtDesc(projectId, p);
            }
        }
        return ResponseEntity.ok(list.stream().map(UxEvaluationDto::fromEntity).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UxEvaluationDto> detail(@PathVariable Long id) {
        return uxEvaluationRepository.findById(id)
                .map(UxEvaluationDto::fromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{id}/screenshot", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> screenshot(@PathVariable Long id) {
        Path screenshotPath = uxEvaluationService.resolveScreenshotPath(id);
        if (screenshotPath == null) {
            return ResponseEntity.notFound().build();
        }
        FileSystemResource resource = new FileSystemResource(screenshotPath);
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(resource);
    }
}
