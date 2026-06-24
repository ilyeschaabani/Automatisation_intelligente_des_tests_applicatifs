package com.pfe.platform.authenticationmicroservice.Config;

import com.pfe.platform.authenticationmicroservice.Entity.GlobalRole;
import com.pfe.platform.authenticationmicroservice.Entity.User;
import com.pfe.platform.authenticationmicroservice.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Au demarrage, promeut un utilisateur (par email) au role ADMIN s'il ne l'a pas deja.
 * Indispensable pour amorcer la gestion des roles : sans cela, aucun compte ne peut
 * acceder a /api/admin/** pour assigner des roles aux autres.
 *
 * Configurable via la propriete app.bootstrap.admin-email (vide = desactive).
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;

    @Value("${app.bootstrap.admin-email:}")
    private String adminEmail;

    @Override
    public void run(String... args) {
        if (adminEmail == null || adminEmail.isBlank()) {
            return;
        }
        String normalized = adminEmail.trim().toLowerCase();
        userRepository.findByEmail(normalized).ifPresentOrElse(user -> {
            if (user.getGlobalRoles() == null || !user.getGlobalRoles().contains(GlobalRole.ADMIN)) {
                user.getGlobalRoles().add(GlobalRole.ADMIN);
                userRepository.save(user);
                log.info("[AdminBootstrap] Role ADMIN attribue a {}", normalized);
            }
        }, () -> log.warn("[AdminBootstrap] Aucun utilisateur avec l'email {} (admin non amorce)", normalized));
    }
}
