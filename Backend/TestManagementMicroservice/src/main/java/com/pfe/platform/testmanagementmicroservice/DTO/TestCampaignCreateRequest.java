package com.pfe.platform.testmanagementmicroservice.DTO;

import com.pfe.platform.testmanagementmicroservice.entity.Enum.SessionStatus;

import java.time.Instant;
import java.time.LocalDate;

public record TestCampaignCreateRequest(
        Long projectId,
        String name,
        String version,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        String environment,
        String triggerType,
        SessionStatus sessionStatus,
        Instant executionStartDate,
        Instant executionEndDate,
        String createdBy
) {
}

