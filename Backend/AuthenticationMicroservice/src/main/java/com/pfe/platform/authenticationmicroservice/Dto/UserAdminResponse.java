package com.pfe.platform.authenticationmicroservice.Dto;

import com.pfe.platform.authenticationmicroservice.Entity.GlobalRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Vue d'un utilisateur pour l'ecran d'administration des roles.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAdminResponse {
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String imageUrl;
    private String githubUsername;
    private Boolean githubConnected;
    private Set<GlobalRole> roles;
}
