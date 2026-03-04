package com.pfe.platform.testmanagementmicroservice.service.TestExecusion;

import com.pfe.platform.testmanagementmicroservice.DTO.TestExecutionDto;
import com.pfe.platform.testmanagementmicroservice.entity.TestExecution;

public final class TestExecutionMapper {

    private TestExecutionMapper() {
    }

    public static TestExecutionDto toDto(TestExecution e) {
        return new TestExecutionDto(
                e.getId(),
                e.getExecutionNumber(),
                e.getExecutionDate(),
                e.getExecutionType(),
                e.getStatus(),
                e.getCampaign() != null ? e.getCampaign().getId() : null
        );
    }
}
