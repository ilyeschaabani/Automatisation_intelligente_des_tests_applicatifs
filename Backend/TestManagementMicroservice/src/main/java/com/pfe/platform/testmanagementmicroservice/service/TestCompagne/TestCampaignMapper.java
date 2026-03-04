package com.pfe.platform.testmanagementmicroservice.service.TestCompagne;

import com.pfe.platform.testmanagementmicroservice.DTO.TestCampaignDto;
import com.pfe.platform.testmanagementmicroservice.entity.TestCampaign;

import java.util.List;

public final class TestCampaignMapper {

    private TestCampaignMapper() {
    }

    public static TestCampaignDto toDto(TestCampaign c) {
        List<Long> testCaseIds = (c.getTestCases() == null)
                ? List.of()
                : c.getTestCases().stream().map(tc -> tc.getId()).toList();

        return new TestCampaignDto(
                c.getId(),
                c.getName(),
                c.getVersion(),
                c.getStatus(),
                c.getStartDate(),
                c.getEndDate(),
                c.getEnvironment(),
                c.getTriggerType(),
                c.getSessionStatus(),
                c.getExecutionStartDate(),
                c.getExecutionEndDate(),
                c.getProject() != null ? c.getProject().getId() : null,
                testCaseIds,
                c.getCreatedAt(),
                c.getCreatedBy()
        );
    }
}
