package com.pfe.platform.ms_gestion.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateEnvironmentRequest {
    @NotBlank
    private String name;
    private String baseUrlWeb;
    private String baseUrlApi;
    private String variables; // JSON string
}
