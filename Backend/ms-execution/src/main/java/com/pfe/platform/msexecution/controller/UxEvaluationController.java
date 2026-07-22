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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping({"/api/functional-evaluation", "/api/intelligence/ux-evaluations"})
@RequiredArgsConstructor
public class UxEvaluationController {

    @Value("${ms-gestion.base-url:http://localhost:8082}")
    private String msGestionUrl;

    private final UxEvaluationRepository uxEvaluationRepository;
    private final UxNavigationStepRepository stepRepository;
    private final AgenticEvaluationService agenticService;
    private final com.pfe.platform.msexecution.repository.FunctionalTestResultRepository functionalTestResultRepository;
    private final EvaluationAccessService evaluationAccessService;
    private final EvaluationMemberRepository evaluationMemberRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'TESTEUR')")
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
        String safeName = file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
        String filename = System.currentTimeMillis() + "_" + safeName;
        Path dest = uploadDir.resolve(filename);
        file.transferTo(dest.toFile());
        return ResponseEntity.ok(Map.of("path", dest.toString(), "filename", filename));
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'TESTEUR')")
    public ResponseEntity<Void> execute(@PathVariable Long id) {
        evaluationAccessService.checkAccess(id);
        agenticService.executeEvaluation(id);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'TESTEUR')")
    public ResponseEntity<Void> stop(@PathVariable Long id) {
        evaluationAccessService.checkAccess(id);
        agenticService.stopEvaluation(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'TESTEUR')")
    public ResponseEntity<Void> pause(@PathVariable Long id) {
        evaluationAccessService.checkAccess(id);
        agenticService.pauseEvaluation(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'TESTEUR')")
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
        List<UxEvaluation.Platform> platforms = parsePlatforms(platform);

        if (accessibleIds == null) {
            if (projectId != null) {
                list = platforms == null
                        ? uxEvaluationRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                        : uxEvaluationRepository.findByProjectIdAndPlatformInOrderByCreatedAtDesc(projectId, platforms);
            } else {
                list = platforms == null
                        ? uxEvaluationRepository.findAllByOrderByCreatedAtDesc()
                        : uxEvaluationRepository.findByPlatformInOrderByCreatedAtDesc(platforms);
            }
        } else if (accessibleIds.isEmpty()) {
            list = List.of();
        } else {
            list = platforms == null
                    ? uxEvaluationRepository.findByIdInOrderByCreatedAtDesc(accessibleIds)
                    : uxEvaluationRepository.findByIdInAndPlatformInOrderByCreatedAtDesc(accessibleIds, platforms);
            if (projectId != null) {
                list = list.stream().filter(e -> projectId.equals(e.getProjectId())).toList();
            }
        }
        List<UxEvaluationDto> dtos = list.stream().map(UxEvaluationDto::fromEntity).collect(Collectors.toList());
        if (!dtos.isEmpty()) {
            List<Long> evalIds = dtos.stream().map(UxEvaluationDto::getId).toList();
            Map<Long, Long> ownerMap = evaluationMemberRepository
                    .findByEvaluationIdInAndRole(evalIds, EvaluationMember.Role.OWNER)
                    .stream()
                    .collect(Collectors.toMap(EvaluationMember::getEvaluationId, EvaluationMember::getUserId, (a, b) -> a));
            dtos.forEach(d -> d.setOwnerUserId(ownerMap.get(d.getId())));
        }
        return ResponseEntity.ok(dtos);
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
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER')")
    public ResponseEntity<Map<String, String>> addMember(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        evaluationAccessService.checkAccess(id);
        Long userId = ((Number) body.get("userId")).longValue();

        if (evaluationMemberRepository.existsByEvaluationIdAndUserId(id, userId)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cet utilisateur est déjà membre de cette évaluation"));
        }

        evaluationMemberRepository.save(new EvaluationMember(id, userId, EvaluationMember.Role.TESTER));

        UxEvaluation evaluation = uxEvaluationRepository.findById(id).orElse(null);
        String evalName = evaluation != null && evaluation.getDescription() != null
                ? evaluation.getDescription() : "Évaluation #" + id;
        notifyMemberAdded(userId, "evaluation", evalName, "/functional-evaluation/" + id, id);

        return ResponseEntity.ok(Map.of("message", "Membre ajouté"));
    }

    private void notifyMemberAdded(Long userId, String context, String contextName, String link, Long contextId) {
        try {
            RestTemplate rt = new RestTemplate();
            Map<String, Object> payload = Map.of(
                    "userId", userId,
                    "context", context,
                    "contextName", contextName,
                    "link", link,
                    "contextId", contextId
            );
            rt.postForEntity(msGestionUrl + "/api/internal/notifications/member-added", payload, Map.class);
        } catch (Exception e) {
            log.warn("Failed to send member notification for user {}: {}", userId, e.getMessage());
        }
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER')")
    @Transactional
    public ResponseEntity<Map<String, String>> removeMember(
            @PathVariable Long id,
            @PathVariable Long userId) {
        evaluationAccessService.checkAccess(id);
        evaluationMemberRepository.deleteByEvaluationIdAndUserId(id, userId);
        return ResponseEntity.ok(Map.of("message", "Membre retiré"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'TESTEUR')")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteEvaluation(@PathVariable Long id) {
        if (!uxEvaluationRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.hasGlobalRole("ADMIN");
        boolean isOwner = evaluationMemberRepository.findByEvaluationId(id).stream()
                .anyMatch(m -> m.getRole() == EvaluationMember.Role.OWNER && m.getUserId().equals(currentUserId));
        if (!isAdmin && !isOwner) {
            return ResponseEntity.status(403).body(Map.of("message", "Seul l'admin ou le créateur peut supprimer cette évaluation"));
        }
        stepRepository.deleteByEvaluationId(id);
        functionalTestResultRepository.deleteByEvaluationId(id);
        evaluationMemberRepository.deleteByEvaluationId(id);
        uxEvaluationRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Évaluation supprimée"));
    }

    private List<UxEvaluation.Platform> parsePlatforms(String platform) {
        if (platform == null || platform.isBlank()) return null;
        String upper = platform.toUpperCase();
        return switch (upper) {
            case "WEB" -> List.of(UxEvaluation.Platform.WEB, UxEvaluation.Platform.WEB_DESKTOP, UxEvaluation.Platform.WEB_MOBILE);
            case "MOBILE" -> List.of(UxEvaluation.Platform.MOBILE, UxEvaluation.Platform.MOBILE_APP);
            default -> List.of(UxEvaluation.Platform.valueOf(upper));
        };
    }
}
