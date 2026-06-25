package com.pfe.platform.authenticationmicroservice.Config;

import com.pfe.platform.authenticationmicroservice.Entity.GlobalRole;
import com.pfe.platform.authenticationmicroservice.Entity.User;
import com.pfe.platform.authenticationmicroservice.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin-email:}")
    private String adminEmail;

    @Value("${app.bootstrap.admin-password:Admin@1234}")
    private String adminPassword;

    @Value("${app.bootstrap.admin-nom:Super}")
    private String adminNom;

    @Value("${app.bootstrap.admin-prenom:Admin}")
    private String adminPrenom;

    @Override
    public void run(String... args) {
        if (adminEmail == null || adminEmail.isBlank()) {
            return;
        }
        String normalized = adminEmail.trim().toLowerCase();

        userRepository.findByEmail(normalized).ifPresentOrElse(user -> {
            boolean changed = false;

            if (!Boolean.TRUE.equals(user.getSuperAdmin())) {
                user.setSuperAdmin(true);
                changed = true;
            }
            if (user.getGlobalRoles() == null || !user.getGlobalRoles().contains(GlobalRole.ADMIN)) {
                if (user.getGlobalRoles() == null) user.setGlobalRoles(new HashSet<>());
                user.getGlobalRoles().add(GlobalRole.ADMIN);
                changed = true;
            }
            if (!Boolean.TRUE.equals(user.getEnabled())) {
                user.setEnabled(true);
                changed = true;
            }

            if (changed) {
                userRepository.save(user);
                log.info("[AdminBootstrap] Super Admin mis a jour: {}", normalized);
            }
        }, () -> {
            User admin = new User();
            admin.setEmail(normalized);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setNom(adminNom);
            admin.setPrenom(adminPrenom);
            admin.setEnabled(true);
            admin.setSuperAdmin(true);
            admin.setGlobalRoles(new HashSet<>(Set.of(GlobalRole.ADMIN)));
            userRepository.save(admin);
            log.info("[AdminBootstrap] Super Admin cree: {} (mot de passe par defaut)", normalized);
        });
    }
}
