package com.pfe.platform.ms_gestion.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CampaignResponse {
    private Long id;
    private Long projectId;
    private Long environmentId;
    private String name;
    private String appVersion;
    private String gitBranch;
    private String triggerMode;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
