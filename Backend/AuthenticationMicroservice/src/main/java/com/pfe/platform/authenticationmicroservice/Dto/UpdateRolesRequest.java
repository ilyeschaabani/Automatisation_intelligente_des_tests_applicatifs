package com.pfe.platform.authenticationmicroservice.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Corps de requete pour (re)definir l'ensemble des roles globaux d'un utilisateur.
 * Exemple : { "roles": ["ADMIN", "TESTER"] }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRolesRequest {
    private List<String> roles;
}
