package com.pfe.platform.testmanagementmicroservice.DTO;

import com.pfe.platform.testmanagementmicroservice.entity.Enum.ExecutionStatus;
import com.pfe.platform.testmanagementmicroservice.entity.Enum.ExecutionType;

import java.time.Instant;

public record TestExecutionDto(
        Long id,
        Integer executionNumber,
        Instant executionDate,
        ExecutionType executionType,
        ExecutionStatus status,
        Long campaignId
) {
}
