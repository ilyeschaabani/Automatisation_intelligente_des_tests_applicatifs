package com.pfe.platform.testmanagementmicroservice.service.TestSession;

import com.pfe.platform.testmanagementmicroservice.DTO.TestSessionDto;
import com.pfe.platform.testmanagementmicroservice.entity.TestSession;

public final class TestSessionMapper {

    private TestSessionMapper() {
    }

    public static TestSessionDto toDto(TestSession s) {
        return new TestSessionDto(
                s.getId(),
                s.getStartDate(),
                s.getEndDate(),
                s.getEnvironment(),
                s.getStatus(),
                s.getTriggerType(),
                s.getProject() != null ? s.getProject().getId() : null,
                s.getCampaign() != null ? s.getCampaign().getId() : null
        );
    }
}

