package com.pfe.platform.msexecution.controller;

import com.pfe.platform.msexecution.dto.UxEvaluationDto;
import com.pfe.platform.msexecution.dto.UxNavigationStepDto;
import com.pfe.platform.msexecution.dto.request.UxEvaluationRequest;
import com.pfe.platform.msexecution.entity.EvaluationMember;
import com.pfe.platform.msexecution.entity.UxEvaluation;
import com.pfe.platform.msexecution.repository.EvaluationMemberRepository;
import com.pfe.platform.msexecution.repository.UxEvaluationRepository;
import com.pfe.platform.msexecution.repository.UxNavigationStepRepository;
import com.pfe.platform.msexecution.security.SecurityUtils;
import com.pfe.platform.msexecution.service.AgenticEvaluationService;
import com.pfe.platform.msexecution.service.EvaluationAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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
    private final EvaluationAccessService evaluationAccessService;
    private final EvaluationMemberRepository evaluationMemberRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')")
    public ResponseEntity<UxEvaluationDto> create(@Valid @RequestBody UxEvaluationRequest req) {
        String platform = req.getPlatform() != null ? req.getPlatform() : "WEB";
        UxEvaluation created = agenticService.createEvaluation(
                req.getUrl(), req.getDescription(), req.getProjectId(), platform, req.getApkPath(),
                req.getReviewMode(), req.getScenario());
        evaluationMemberRepository.save(
                new EvaluationMember(created.getId(), SecurityUtils.getCurrentUserId(), EvaluationMember.Role.OWNER));
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
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')")
    public ResponseEntity<Void> execute(@PathVariable Long id) {
        evaluationAccessService.checkAccess(id);
        agenticService.executeEvaluation(id);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')")
    public ResponseEntity<Void> stop(@PathVariable Long id) {
        evaluationAccessService.checkAccess(id);
        agenticService.stopEvaluation(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')")
    public ResponseEntity<Void> pause(@PathVariable Long id) {
        evaluationAccessService.checkAccess(id);
        agenticService.pauseEvaluation(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')")
    public ResponseEntity<Void> resume(@PathVariable Long id) {
        evaluationAccessService.checkAccess(id);
        agenticService.resumeEvaluation(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<UxEvaluationDto>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String platform) {
        List<Long> accessibleIds = evaluationAccessService.getAccessibleEvaluationIds();
        List<UxEvaluation> list;

        if (accessibleIds == null) {
            // ADMIN — sees all
            if (projectId != null) {
                list = platform == null || platform.isBlank()
                        ? uxEvaluationRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                        : uxEvaluationRepository.findByProjectIdAndPlatformOrderByCreatedAtDesc(projectId, parsePlatform(platform));
            } else {
                list = platform == null || platform.isBlank()
                        ? uxEvaluationRepository.findAllByOrderByCreatedAtDesc()
                        : uxEvaluationRepository.findByPlatformOrderByCreatedAtDesc(parsePlatform(platform));
            }
        } else if (accessibleIds.isEmpty()) {
            list = List.of();
        } else {
            list = platform == null || platform.isBlank()
                    ? uxEvaluationRepository.findByIdInOrderByCreatedAtDesc(accessibleIds)
                    : uxEvaluationRepository.findByIdInAndPlatformOrderByCreatedAtDesc(accessibleIds, parsePlatform(platform));
            if (projectId != null) {
                list = list.stream().filter(e -> projectId.equals(e.getProjectId())).toList();
            }
        }
        return ResponseEntity.ok(list.stream().map(UxEvaluationDto::fromEntity).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UxEvaluationDto> detail(@PathVariable Long id) {
        evaluationAccessService.checkAccess(id);
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

    @GetMapping("/{id}/steps")
    public ResponseEntity<List<UxNavigationStepDto>> getSteps(@PathVariable Long id) {
        evaluationAccessService.checkAccess(id);
        List<UxNavigationStepDto> steps = stepRepository
                .findByEvaluationIdOrderByStepNumberAsc(id)
                .stream()
                .map(UxNavigationStepDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(steps);
    }

    @GetMapping("/{id}/functional-results")
    public ResponseEntity<List<com.pfe.platform.msexecution.entity.FunctionalTestResultEntity>> getFunctionalResults(
            @PathVariable Long id) {
        evaluationAccessService.checkAccess(id);
        return ResponseEntity.ok(functionalTestResultRepository.findByEvaluationIdOrderByIdAsc(id));
    }

    // ─── Member management ───────────────────────────────────────

    @GetMapping("/{id}/members")
    public ResponseEntity<List<Map<String, Object>>> listMembers(@PathVariable Long id) {
        evaluationAccessService.checkAccess(id);
        List<EvaluationMember> members = evaluationMemberRepository.findByEvaluationId(id);
        List<Map<String, Object>> result = members.stream().map(m -> Map.<String, Object>of(
                "id", m.getId(),
                "userId", m.getUserId(),
                "role", m.getRole().name(),
                "assignedAt", m.getAssignedAt() != null ? m.getAssignedAt().toString() : ""
        )).toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')")
    public ResponseEntity<Map<String, String>> addMember(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        evaluationAccessService.checkAccess(id);
        Long userId = ((Number) body.get("userId")).longValue();

        if (evaluationMemberRepository.existsByEvaluationIdAndUserId(id, userId)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cet utilisateur est déjà membre de cette évaluation"));
        }

        evaluationMemberRepository.save(new EvaluationMember(id, userId, EvaluationMember.Role.TESTER));
        return ResponseEntity.ok(Map.of("message", "Membre ajouté"));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')")
    @Transactional
    public ResponseEntity<Map<String, String>> removeMember(
            @PathVariable Long id,
            @PathVariable Long userId) {
        evaluationAccessService.checkAccess(id);
        evaluationMemberRepository.deleteByEvaluationIdAndUserId(id, userId);
        return ResponseEntity.ok(Map.of("message", "Membre retiré"));
    }

    private UxEvaluation.Platform parsePlatform(String platform) {
        return UxEvaluation.Platform.valueOf(platform.toUpperCase());
    }
}
