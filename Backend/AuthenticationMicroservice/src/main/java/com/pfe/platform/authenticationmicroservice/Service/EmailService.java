package com.pfe.platform.authenticationmicroservice.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@testauto.com}")
    private String fromAddress;

    @Value("${app.mail.platform-name:TestAuto}")
    private String platformName;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendUrl;

    @Async
    public void sendPasswordResetEmail(String toEmail, String userName, String tempPassword) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(platformName + " — Votre mot de passe a été réinitialisé");

            String html = buildResetEmailHtml(userName, tempPassword);
            helper.setText(html, true);

            log.info("Sending password reset email to {} (from={})", toEmail, fromAddress);
            mailSender.send(message);
            log.info("Password reset email sent to {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to build password reset email for {}", toEmail, e);
        } catch (Exception e) {
            // MailException (auth/connection) is an unchecked RuntimeException and
            // was previously swallowed by the @Async executor with no trace.
            log.error("Failed to send password reset email to {} — check SMTP credentials / Gmail app password", toEmail, e);
        }
    }

    @Async
    public void sendWelcomeEmail(String toEmail, String userName, String generatedPassword) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(platformName + " — Votre compte a été créé");

            String html = buildWelcomeEmailHtml(userName, toEmail, generatedPassword);
            helper.setText(html, true);

            log.info("Sending welcome email to {} (from={})", toEmail, fromAddress);
            mailSender.send(message);
            log.info("Welcome email sent to {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to build welcome email for {}", toEmail, e);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {} — check SMTP credentials / Gmail app password", toEmail, e);
        }
    }

    /**
     * Synchronous test send. Throws on failure so the caller (a test endpoint)
     * can surface the exact SMTP error instead of it being swallowed by @Async.
     */
    public void sendTestEmail(String toEmail) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(toEmail);
        helper.setSubject(platformName + " — Test d'envoi");
        helper.setText("<p>Ceci est un e-mail de test de la plateforme " + platformName + ".</p>", true);
        mailSender.send(message);
        log.info("Test email sent to {} (from={})", toEmail, fromAddress);
    }

    private String buildResetEmailHtml(String userName, String tempPassword) {
        return """
            <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 520px; margin: 0 auto; padding: 32px; border: 1px solid #e5e7eb; border-radius: 12px;">
                <div style="text-align: center; margin-bottom: 24px;">
                    <h1 style="color: #1a1a2e; font-size: 22px; margin: 0;">🔐 %s</h1>
                    <p style="color: #6b7280; font-size: 13px; margin-top: 4px;">Plateforme d'automatisation des tests</p>
                </div>
                <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 16px 0;" />
                <p style="color: #374151; font-size: 15px;">Bonjour <strong>%s</strong>,</p>
                <p style="color: #374151; font-size: 14px;">
                    Votre demande de réinitialisation de mot de passe a été approuvée par l'administrateur.
                    Voici votre mot de passe temporaire :
                </p>
                <div style="background: #f0f9ff; border: 2px dashed #3b82f6; border-radius: 8px; padding: 16px; text-align: center; margin: 20px 0;">
                    <p style="color: #6b7280; font-size: 12px; margin: 0 0 8px 0; text-transform: uppercase; letter-spacing: 1px;">Mot de passe temporaire</p>
                    <p style="color: #1e40af; font-size: 24px; font-weight: bold; font-family: monospace; margin: 0; letter-spacing: 3px;">%s</p>
                </div>
                <p style="color: #374151; font-size: 14px;">
                    Connectez-vous avec ce mot de passe, puis rendez-vous dans <strong>Mon Profil → Sécurité</strong> pour le modifier.
                </p>
                <div style="text-align: center; margin: 24px 0;">
                    <a href="%s/login" style="display: inline-block; background: #2563eb; color: white; padding: 12px 32px; border-radius: 8px; text-decoration: none; font-weight: 600; font-size: 14px;">
                        Se connecter
                    </a>
                </div>
                <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 16px 0;" />
                <p style="color: #9ca3af; font-size: 11px; text-align: center;">
                    Si vous n'avez pas demandé cette réinitialisation, contactez immédiatement votre administrateur.
                </p>
            </div>
            """.formatted(platformName, userName, tempPassword, frontendUrl);
    }

    private String buildWelcomeEmailHtml(String userName, String email, String password) {
        return """
            <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 520px; margin: 0 auto; padding: 32px; border: 1px solid #e5e7eb; border-radius: 12px;">
                <div style="text-align: center; margin-bottom: 24px;">
                    <h1 style="color: #1a1a2e; font-size: 22px; margin: 0;">🎉 %s</h1>
                    <p style="color: #6b7280; font-size: 13px; margin-top: 4px;">Plateforme d'automatisation des tests</p>
                </div>
                <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 16px 0;" />
                <p style="color: #374151; font-size: 15px;">Bonjour <strong>%s</strong>,</p>
                <p style="color: #374151; font-size: 14px;">
                    Votre compte a été créé par l'administrateur. Voici vos identifiants de connexion :
                </p>
                <div style="background: #f0f9ff; border: 2px dashed #3b82f6; border-radius: 8px; padding: 16px; margin: 20px 0;">
                    <p style="color: #6b7280; font-size: 12px; margin: 0 0 8px 0; text-transform: uppercase; letter-spacing: 1px;">Email</p>
                    <p style="color: #1e40af; font-size: 16px; font-weight: bold; font-family: monospace; margin: 0 0 16px 0;">%s</p>
                    <p style="color: #6b7280; font-size: 12px; margin: 0 0 8px 0; text-transform: uppercase; letter-spacing: 1px;">Mot de passe</p>
                    <p style="color: #1e40af; font-size: 24px; font-weight: bold; font-family: monospace; margin: 0; letter-spacing: 3px;">%s</p>
                </div>
                <p style="color: #374151; font-size: 14px;">
                    Connectez-vous avec ces identifiants, puis rendez-vous dans <strong>Mon Profil → Sécurité</strong> pour changer votre mot de passe.
                </p>
                <div style="text-align: center; margin: 24px 0;">
                    <a href="%s/login" style="display: inline-block; background: #2563eb; color: white; padding: 12px 32px; border-radius: 8px; text-decoration: none; font-weight: 600; font-size: 14px;">
                        Se connecter
                    </a>
                </div>
                <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 16px 0;" />
                <p style="color: #9ca3af; font-size: 11px; text-align: center;">
                    Si vous n'êtes pas à l'origine de cette inscription, veuillez ignorer cet e-mail.
                </p>
            </div>
            """.formatted(platformName, userName, email, password, frontendUrl);
    }
}
