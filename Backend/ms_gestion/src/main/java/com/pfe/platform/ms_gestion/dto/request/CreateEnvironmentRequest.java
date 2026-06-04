package com.pfe.platform.ms_gestion.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateEnvironmentRequest {
    @NotBlank
    private String name;

    private String baseUrlWeb;   // URL cible pour les tests WEB/UX
    private String baseUrlApi;   // URL cible pour les tests API/INTEGRATION

    private String gitRepoUrl;   // Repo du code source à tester (UNIT/INTEGRATION)
    private String gitBranch;    // Branche du repo source (défaut: main)

    private String databaseType; // POSTGRESQL | MYSQL | H2 | MONGODB (pour tests INTEGRATION)
}
