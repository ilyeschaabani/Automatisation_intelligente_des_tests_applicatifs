package com.pfe.platform.testmanagementmicroservice.DTO;

import java.util.List;

public record TestCampaignSetTestCasesRequest(
        List<Long> testCaseIds
) {
}

