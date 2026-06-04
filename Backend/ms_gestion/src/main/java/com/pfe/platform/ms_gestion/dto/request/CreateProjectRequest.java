package com.pfe.platform.ms_gestion.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateProjectRequest {
    @NotBlank(message = "Le nom du projet est obligatoire")
    private String name;
    private String description;
}
