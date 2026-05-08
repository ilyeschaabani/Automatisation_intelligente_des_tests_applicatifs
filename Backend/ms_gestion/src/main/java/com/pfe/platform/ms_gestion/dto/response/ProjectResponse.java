package com.pfe.platform.ms_gestion.dto.response;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProjectResponse {
    private Long id;
    private String name;
    private String description;
    private String gitRepoUrl;
    private String gitDefaultBranch;
    private String status;
    private LocalDateTime createdAt;
    private Boolean aiProject;
}
