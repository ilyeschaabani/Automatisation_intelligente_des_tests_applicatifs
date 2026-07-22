package com.pfe.platform.ms_gestion.controller;

import com.pfe.platform.ms_gestion.entity.Campaign;
import com.pfe.platform.ms_gestion.entity.ExecutionResultRef;
import com.pfe.platform.ms_gestion.entity.Notification;
import com.pfe.platform.ms_gestion.repository.CampaignRepository;
import com.pfe.platform.ms_gestion.repository.ExecutionResultRefRepository;
import com.pfe.platform.ms_gestion.service.EmailService;
import com.pfe.platform.ms_gestion.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/execution-results")
@RequiredArgsConstructor
public class ExecutionResultController {

    private final ExecutionResultRefRepository resultRepository;
    private final CampaignRepository campaignRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER')")
    public ResponseEntity<?> assignResult(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        ExecutionResultRef result = resultRepository.findById(id).orElse(null);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        Long userId = body.get("userId") != null
                ? Long.valueOf(body.get("userId").toString()) : null;
        String name = (String) body.get("name");
        String email = (String) body.get("email");

        if (userId == null || name == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "userId and name are required"));
        }

        result.setAssignedTo(name);
        result.setAssignedToUserId(userId);
        resultRepository.save(result);

        String testTitle = "Test #" + result.getTestCaseId();

        notificationService.create(
                userId,
                "Erreur de test assignée",
                String.format("L'erreur du test \"%s\" vous a été assignée.", testTitle),
                Notification.NotifType.TEST_FAILED,
                "/my-assignments?highlight=result-" + result.getId()
        );

        if (email != null && !email.isBlank()) {
            emailService.sendTestErrorAssignmentEmail(
                    email, name, testTitle,
                    result.getErrorMessage(), result.getId()
            );
        }

        return ResponseEntity.ok(Map.of(
                "id", result.getId(),
                "assignedTo", name
        ));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateResultStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String status = body.get("status");
        if (status == null) return ResponseEntity.badRequest().body(Map.of("message", "status required"));

        ExecutionResultRef result = resultRepository.findById(id).orElse(null);
        if (result == null) return ResponseEntity.notFound().build();

        result.setStatus(status);
        resultRepository.save(result);

        if ("RESOLVED".equalsIgnoreCase(status) && result.getCampaignId() != null) {
            campaignRepository.findById(result.getCampaignId()).ifPresent(campaign -> {
                if (campaign.getProject() != null && campaign.getProject().getCreatedBy() != null) {
                    Long ownerId = campaign.getProject().getCreatedBy();
                    if (!ownerId.equals(result.getAssignedToUserId())) {
                        notificationService.create(
                                ownerId,
                                "Erreur de test résolue",
                                String.format("L'erreur du test #%d (campagne #%d) a été marquée comme résolue par %s.",
                                        result.getTestCaseId(), result.getCampaignId(),
                                        result.getAssignedTo() != null ? result.getAssignedTo() : "un membre"),
                                Notification.NotifType.TEST_RESOLVED,
                                "/campaigns/" + result.getCampaignId()
                        );
                    }
                }
            });
        }

        return ResponseEntity.ok(Map.of("id", result.getId(), "status", status));
    }
}
