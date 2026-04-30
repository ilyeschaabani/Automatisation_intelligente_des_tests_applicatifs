package com.pfe.platform.ms_gestion.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EnvironmentResponse {
    private Long id;
    private String name;
    private String baseUrlWeb;
    private String baseUrlApi;
    private String variables;
    private LocalDateTime createdAt;
}
