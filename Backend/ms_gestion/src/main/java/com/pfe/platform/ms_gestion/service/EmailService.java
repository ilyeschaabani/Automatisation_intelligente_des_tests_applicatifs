package com.pfe.platform.ms_gestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Async
    public void sendAssignmentEmail(String toEmail, String assigneeName,
                                     String vulnTitle, String severity, Long vulnId) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("[Test Platform] Vulnérabilité assignée : " + vulnTitle);
            message.setText(String.format(
                    "Bonjour %s,\n\n" +
                    "Une vulnérabilité vous a été assignée sur la plateforme de test.\n\n" +
                    "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                    "Titre : %s\n" +
                    "Sévérité : %s\n" +
                    "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                    "Connectez-vous à la plateforme pour consulter les détails et prendre en charge cette vulnérabilité.\n\n" +
                    "Cordialement,\n" +
                    "Plateforme de Test Automatisé",
                    assigneeName, vulnTitle, severity
            ));
            mailSender.send(message);
            log.info("Assignment email sent to {} for vulnerability {}", toEmail, vulnId);
        } catch (Exception e) {
            log.error("Failed to send assignment email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendTestErrorAssignmentEmail(String toEmail, String assigneeName,
                                              String testTitle, String errorMessage, Long resultId) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("[Test Platform] Erreur de test assignée : " + testTitle);
            message.setText(String.format(
                    "Bonjour %s,\n\n" +
                    "Une erreur de test vous a été assignée sur la plateforme de test.\n\n" +
                    "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                    "Test : %s\n" +
                    "Erreur : %s\n" +
                    "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                    "Connectez-vous à la plateforme pour consulter les détails et corriger cette erreur.\n\n" +
                    "Cordialement,\n" +
                    "Plateforme de Test Automatisé",
                    assigneeName, testTitle,
                    errorMessage != null ? errorMessage : "Voir les détails sur la plateforme"
            ));
            mailSender.send(message);
            log.info("Test error assignment email sent to {} for result {}", toEmail, resultId);
        } catch (Exception e) {
            log.error("Failed to send test error assignment email to {}: {}", toEmail, e.getMessage());
        }
    }
}
