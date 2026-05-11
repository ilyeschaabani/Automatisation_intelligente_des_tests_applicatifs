package com.pfe.platform.ms_gestion.dto.response;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TestSuiteResponse {
    private Long id;
    private String name;
    private String description;
    private String type;
    private String gitRepoUrl;
    private String gitBranch;
    private LocalDateTime createdAt;
}
