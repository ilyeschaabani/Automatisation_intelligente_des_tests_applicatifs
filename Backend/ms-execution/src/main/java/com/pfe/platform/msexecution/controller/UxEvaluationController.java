package com.pfe.platform.msexecution.controller;

import com.pfe.platform.msexecution.dto.UxEvaluationDto;
import com.pfe.platform.msexecution.dto.UxNavigationStepDto;
import com.pfe.platform.msexecution.dto.request.UxEvaluationRequest;
import com.pfe.platform.msexecution.entity.UxEvaluation;
import com.pfe.platform.msexecution.entity.UxNavigationStep;
import com.pfe.platform.msexecution.repository.UxEvaluationRepository;
import com.pfe.platform.msexecution.repository.UxNavigationStepRepository;
import com.pfe.platform.msexecution.service.AgenticEvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping({"/api/functional-evaluation", "/api/intelligence/ux-evaluations"})
@RequiredArgsConstructor
public class UxEvaluationController {

    private final UxEvaluationRepository uxEvaluationRepository;
    private final UxNavigationStepRepository stepRepository;
    private final AgenticEvaluationService agenticService;
    private final com.pfe.platform.msexecution.repository.FunctionalTestResultRepository functionalTestResultRepository;

    @PostMapping
    public ResponseEntity<UxEvaluationDto> create(@Valid @RequestBody UxEvaluationRequest req) {
        String platform = req.getPlatform() != null ? req.getPlatform() : "WEB";
        UxEvaluation created = agenticService.createEvaluation(
                req.getUrl(), req.getDescription(), req.getProjectId(), platform, req.getApkPath(), req.getReviewMode());
        return ResponseEntity.ok(UxEvaluationDto.fromEntity(created));
    }

    @PostMapping("/upload-apk")
    public ResponseEntity<Map<String, String>> uploadApk(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename().endsWith(".apk")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Fichier APK invalide"));
        }
        Path uploadDir = Path.of(System.getProperty("java.io.tmpdir"), "ms-execution", "apks");
        Files.createDirectories(uploadDir);
        String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path dest = uploadDir.resolve(filename);
        file.transferTo(dest.toFile());
        return ResponseEntity.ok(Map.of("path", dest.toString(), "filename", filename));
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<Void> execute(@PathVariable Long id) {
        agenticService.executeEvaluation(id);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<Void> stop(@PathVariable Long id) {
        agenticService.stopEvaluation(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<Void> pause(@PathVariable Long id) {
        agenticService.pauseEvaluation(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<Void> resume(@PathVariable Long id) {
        agenticService.resumeEvaluation(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<UxEvaluationDto>> list(
            @RequestParam(required = false) Long projectId,
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

    /**
     * Detail endpoint — loads navigation steps too.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UxEvaluationDto> detail(@PathVariable Long id) {
        return uxEvaluationRepository.findById(id)
                .map(evaluation -> {
                    List<UxNavigationStepDto> steps = stepRepository
                            .findByEvaluationIdOrderByStepNumberAsc(id)
                            .stream()
                            .map(UxNavigationStepDto::fromEntity)
                            .collect(Collectors.toList());
                    return UxEvaluationDto.fromEntity(evaluation, steps);
                })
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Get steps for a specific evaluation (used for live polling during RUNNING).
     */
    @GetMapping("/{id}/steps")
    public ResponseEntity<List<UxNavigationStepDto>> getSteps(@PathVariable Long id) {
        List<UxNavigationStepDto> steps = stepRepository
                .findByEvaluationIdOrderByStepNumberAsc(id)
                .stream()
                .map(UxNavigationStepDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(steps);
    }

    /** Résultats détaillés des tests fonctionnels d'une évaluation. */
    @GetMapping("/{id}/functional-results")
    public ResponseEntity<List<com.pfe.platform.msexecution.entity.FunctionalTestResultEntity>> getFunctionalResults(
            @PathVariable Long id) {
        return ResponseEntity.ok(functionalTestResultRepository.findByEvaluationIdOrderByIdAsc(id));
    }
}
