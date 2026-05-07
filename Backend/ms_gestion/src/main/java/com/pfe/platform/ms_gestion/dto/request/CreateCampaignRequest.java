package com.pfe.platform.ms_gestion.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateCampaignRequest {
    @NotBlank
    private String name;
    private String appVersion;
    private String gitBranch;                // si vide, on utilisera la branche par défaut du projet

    @NotNull
    private Long environmentId;

    private String triggerMode = "MANUAL";   // MANUAL, SCHEDULED, CI

    // Liste des IDs des cas de test à inclure
    private List<Long> testCaseIds;
}
