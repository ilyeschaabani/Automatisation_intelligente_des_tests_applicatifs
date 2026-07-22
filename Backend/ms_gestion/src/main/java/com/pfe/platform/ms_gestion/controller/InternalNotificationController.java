package com.pfe.platform.ms_gestion.controller;

import com.pfe.platform.ms_gestion.entity.Notification;
import com.pfe.platform.ms_gestion.entity.UserRef;
import com.pfe.platform.ms_gestion.repository.UserRefRepository;
import com.pfe.platform.ms_gestion.service.EmailService;
import com.pfe.platform.ms_gestion.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/internal/notifications")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final UserRefRepository userRefRepository;

    @PostMapping("/member-added")
    public ResponseEntity<Map<String, String>> notifyMemberAdded(@RequestBody Map<String, Object> body) {
        Long userId = ((Number) body.get("userId")).longValue();
        String context = (String) body.getOrDefault("context", "");
        String contextName = (String) body.getOrDefault("contextName", "");
        String link = (String) body.getOrDefault("link", "");
        Long contextId = body.get("contextId") != null ? ((Number) body.get("contextId")).longValue() : null;

        UserRef user = userRefRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Utilisateur introuvable"));
        }

        String displayName = buildDisplayName(user);
        Notification.NotifType type;
        String title;
        String message;

        if ("evaluation".equals(context)) {
            type = Notification.NotifType.EVALUATION_MEMBER_ADDED;
            title = "Ajouté à une évaluation";
            message = "Vous avez été ajouté à l'évaluation « " + contextName + " ».";
            emailService.sendEvaluationMemberEmail(user.getEmail(), displayName, contextName, contextId);
        } else {
            type = Notification.NotifType.PROJECT_MEMBER_ADDED;
            title = "Ajouté au projet";
            message = "Vous avez été ajouté au projet « " + contextName + " ».";
            emailService.sendProjectMemberEmail(user.getEmail(), displayName, contextName, contextId);
        }

        notificationService.create(userId, title, message, type, link);

        return ResponseEntity.ok(Map.of("message", "Notification envoyée"));
    }

    private String buildDisplayName(UserRef user) {
        String name = "";
        if (user.getPrenom() != null) name += user.getPrenom();
        if (user.getNom() != null) name += (name.isEmpty() ? "" : " ") + user.getNom();
        return name.isEmpty() ? user.getEmail() : name;
    }
}
