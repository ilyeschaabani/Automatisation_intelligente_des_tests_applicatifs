package com.pfe.platform.testmanagementmicroservice.DTO;

import com.pfe.platform.testmanagementmicroservice.entity.Endpoint;
import com.pfe.platform.testmanagementmicroservice.entity.Enum.SessionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TestCampaignDto(
        Long id,
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
        Long projectId,
        List<Long> testCaseIds,
        Instant createdAt,
        String createdBy

) {
}
