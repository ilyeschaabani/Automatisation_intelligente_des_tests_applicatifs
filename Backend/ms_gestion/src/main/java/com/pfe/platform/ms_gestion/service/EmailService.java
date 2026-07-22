package com.pfe.platform.ms_gestion.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.properties.mail.from:noreply@testauto.com}")
    private String fromAddress;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendUrl;

    private static final String PLATFORM_NAME = "TestAuto";

    // ─── Vulnerability Assignment ───────────────────────────────

    @Async
    public void sendAssignmentEmail(String toEmail, String assigneeName,
                                     String vulnTitle, String severity, Long vulnId) {
        try {
            String severityColor = switch (severity != null ? severity.toUpperCase() : "") {
                case "CRITICAL" -> "#dc2626";
                case "HIGH" -> "#ea580c";
                case "MEDIUM" -> "#d97706";
                default -> "#2563eb";
            };

            String html = wrapInLayout("🛡️", "Vulnérabilité assignée",
                    assigneeName,
                    "Une vulnérabilité vous a été assignée. Veuillez la consulter et la prendre en charge.",
                    """
                    <div style="background: #fef2f2; border-left: 4px solid %s; border-radius: 8px; padding: 16px; margin: 20px 0;">
                        <p style="color: #6b7280; font-size: 12px; margin: 0 0 6px 0; text-transform: uppercase; letter-spacing: 1px;">Vulnérabilité</p>
                        <p style="color: #1e293b; font-size: 16px; font-weight: bold; margin: 0 0 8px 0;">%s</p>
                        <span style="display: inline-block; background: %s; color: white; padding: 2px 10px; border-radius: 12px; font-size: 12px; font-weight: 600;">%s</span>
                    </div>
                    """.formatted(severityColor, vulnTitle, severityColor, severity != null ? severity : "N/A"),
                    frontendUrl + "/my-assignments?highlight=vuln-" + vulnId,
                    "Voir la vulnérabilité");

            sendHtml(toEmail, PLATFORM_NAME + " — Vulnérabilité assignée : " + vulnTitle, html);
            log.info("Assignment email sent to {} for vulnerability {}", toEmail, vulnId);
        } catch (Exception e) {
            log.error("Failed to send assignment email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ─── Test Error Assignment ───────────────────────────────────

    @Async
    public void sendTestErrorAssignmentEmail(String toEmail, String assigneeName,
                                              String testTitle, String errorMessage, Long resultId) {
        try {
            String errorDisplay = errorMessage != null ? errorMessage : "Voir les détails sur la plateforme";

            String html = wrapInLayout("🧪", "Erreur de test assignée",
                    assigneeName,
                    "Une erreur de test vous a été assignée pour investigation et correction.",
                    """
                    <div style="background: #fff7ed; border-left: 4px solid #ea580c; border-radius: 8px; padding: 16px; margin: 20px 0;">
                        <p style="color: #6b7280; font-size: 12px; margin: 0 0 6px 0; text-transform: uppercase; letter-spacing: 1px;">Test</p>
                        <p style="color: #1e293b; font-size: 16px; font-weight: bold; margin: 0 0 12px 0;">%s</p>
                        <p style="color: #6b7280; font-size: 12px; margin: 0 0 4px 0; text-transform: uppercase; letter-spacing: 1px;">Erreur</p>
                        <p style="color: #9a3412; font-size: 13px; font-family: monospace; margin: 0; background: #fed7aa; padding: 8px; border-radius: 4px;">%s</p>
                    </div>
                    """.formatted(testTitle, errorDisplay),
                    frontendUrl + "/my-assignments?highlight=test-" + resultId,
                    "Voir l'erreur");

            sendHtml(toEmail, PLATFORM_NAME + " — Erreur de test assignée : " + testTitle, html);
            log.info("Test error assignment email sent to {} for result {}", toEmail, resultId);
        } catch (Exception e) {
            log.error("Failed to send test error assignment email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ─── Project Member Added ────────────────────────────────────

    @Async
    public void sendProjectMemberEmail(String toEmail, String memberName, String projectName, Long projectId) {
        try {
            String html = wrapInLayout("📁", "Ajouté au projet",
                    memberName,
                    "Vous avez été ajouté comme membre d'un projet. Vous pouvez désormais accéder à ses ressources, suites de tests et campagnes.",
                    """
                    <div style="background: #f0f9ff; border: 2px dashed #3b82f6; border-radius: 8px; padding: 16px; text-align: center; margin: 20px 0;">
                        <p style="color: #6b7280; font-size: 12px; margin: 0 0 8px 0; text-transform: uppercase; letter-spacing: 1px;">Projet</p>
                        <p style="color: #1e40af; font-size: 20px; font-weight: bold; margin: 0;">%s</p>
                    </div>
                    """.formatted(projectName),
                    frontendUrl + "/projects/" + projectId,
                    "Accéder au projet");

            sendHtml(toEmail, PLATFORM_NAME + " — Vous avez été ajouté au projet : " + projectName, html);
            log.info("Project member email sent to {} for project {}", toEmail, projectId);
        } catch (Exception e) {
            log.error("Failed to send project member email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ─── Evaluation Member Added ─────────────────────────────────

    @Async
    public void sendEvaluationMemberEmail(String toEmail, String memberName,
                                           String evaluationDescription, Long evaluationId) {
        try {
            String evalName = evaluationDescription != null ? evaluationDescription : "Évaluation #" + evaluationId;

            String html = wrapInLayout("🔍", "Ajouté à une évaluation UX",
                    memberName,
                    "Vous avez été ajouté comme membre d'une évaluation UX. Vous pouvez consulter les résultats et participer à l'analyse.",
                    """
                    <div style="background: #faf5ff; border: 2px dashed #8b5cf6; border-radius: 8px; padding: 16px; text-align: center; margin: 20px 0;">
                        <p style="color: #6b7280; font-size: 12px; margin: 0 0 8px 0; text-transform: uppercase; letter-spacing: 1px;">Évaluation UX</p>
                        <p style="color: #6d28d9; font-size: 20px; font-weight: bold; margin: 0;">%s</p>
                    </div>
                    """.formatted(evalName),
                    frontendUrl + "/functional-evaluation/" + evaluationId,
                    "Voir l'évaluation");

            sendHtml(toEmail, PLATFORM_NAME + " — Ajouté à une évaluation UX", html);
            log.info("Evaluation member email sent to {} for evaluation {}", toEmail, evaluationId);
        } catch (Exception e) {
            log.error("Failed to send evaluation member email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ─── Shared HTML layout ──────────────────────────────────────

    private String wrapInLayout(String icon, String heading, String userName,
                                 String introText, String contentBlock,
                                 String actionUrl, String actionLabel) {
        return """
            <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 520px; margin: 0 auto; padding: 32px; border: 1px solid #e5e7eb; border-radius: 12px; background: #ffffff;">
                <div style="text-align: center; margin-bottom: 24px;">
                    <h1 style="color: #1a1a2e; font-size: 22px; margin: 0;">%s %s</h1>
                    <p style="color: #6b7280; font-size: 13px; margin-top: 4px;">Plateforme d'automatisation des tests</p>
                </div>
                <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 16px 0;" />
                <p style="color: #374151; font-size: 15px;">Bonjour <strong>%s</strong>,</p>
                <p style="color: #374151; font-size: 14px;">%s</p>
                %s
                <div style="text-align: center; margin: 24px 0;">
                    <a href="%s" style="display: inline-block; background: #2563eb; color: white; padding: 12px 32px; border-radius: 8px; text-decoration: none; font-weight: 600; font-size: 14px;">
                        %s
                    </a>
                </div>
                <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 16px 0;" />
                <p style="color: #9ca3af; font-size: 11px; text-align: center;">
                    Cet e-mail a été envoyé automatiquement par %s. Si vous pensez l'avoir reçu par erreur, contactez votre administrateur.
                </p>
            </div>
            """.formatted(icon, heading, userName, introText, contentBlock, actionUrl, actionLabel, PLATFORM_NAME);
    }

    private void sendHtml(String toEmail, String subject, String html) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(message);
    }
}
