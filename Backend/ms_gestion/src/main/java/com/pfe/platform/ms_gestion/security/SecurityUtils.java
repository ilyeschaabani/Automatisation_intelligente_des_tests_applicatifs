package com.pfe.platform.ms_gestion.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    public static boolean hasGlobalRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_" + role));
    }

    public static boolean isGlobalAdmin() {
        return hasGlobalRole("ADMIN");
    }

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof Long value) {
            return value;
        }
        if (principal instanceof Integer value) {
            return value.longValue();
        }
        if (principal instanceof Number value) {
            return value.longValue();
        }
        if (principal instanceof String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Principal utilisateur invalide (String non numérique)");
            }
        }

        throw new RuntimeException("Type de principal non supporté: " + principal.getClass().getName());
    }
}
