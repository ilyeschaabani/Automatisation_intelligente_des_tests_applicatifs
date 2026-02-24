package com.pfe.platform.testmanagementmicroservice.DTO;

import com.pfe.platform.testmanagementmicroservice.entity.Enum.SessionStatus;

import java.time.Instant;

public record TestSessionDto(
        Long id,
        Instant startDate,
        Instant endDate,
        String environment,
        SessionStatus status,
        String triggerType,
        Long projectId,
        Long campaignId
) {
}

