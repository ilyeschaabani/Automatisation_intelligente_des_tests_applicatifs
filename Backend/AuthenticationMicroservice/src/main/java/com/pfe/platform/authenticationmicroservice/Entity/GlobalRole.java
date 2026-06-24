package com.pfe.platform.authenticationmicroservice.Entity;

/**
 * Roles assignables a un utilisateur au niveau global (plateforme).
 * Un utilisateur peut en cumuler plusieurs (principe de badges).
 * Seul un utilisateur portant le role ADMIN peut gerer les roles des autres.
 */
public enum GlobalRole {
    ADMIN,
    TESTER,
    DEVELOPER
}
