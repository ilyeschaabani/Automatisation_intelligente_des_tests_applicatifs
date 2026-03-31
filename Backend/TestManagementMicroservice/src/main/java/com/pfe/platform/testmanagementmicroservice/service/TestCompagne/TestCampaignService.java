package com.pfe.platform.testmanagementmicroservice.service.TestCompagne;

import com.pfe.platform.testmanagementmicroservice.DTO.EndpointDto;
import com.pfe.platform.testmanagementmicroservice.DTO.TestCampaignCreateRequest;
import com.pfe.platform.testmanagementmicroservice.DTO.TestCampaignSetTestCasesRequest;
import com.pfe.platform.testmanagementmicroservice.DTO.TestCampaignUpdateRequest;
import com.pfe.platform.testmanagementmicroservice.entity.TestCampaign;

import java.util.List;

public interface TestCampaignService {
    List<TestCampaign> findAll();

    List<TestCampaign> findByProject(Long projectId);

    TestCampaign findById(Long id);

    TestCampaign create(TestCampaignCreateRequest request);

    TestCampaign update(Long id, TestCampaignUpdateRequest request);

    TestCampaign setTestCases(Long id, TestCampaignSetTestCasesRequest request);

    void delete(Long id);
    List<EndpointDto> getEndpointsForTestCampaign(Long campaignId);
}
