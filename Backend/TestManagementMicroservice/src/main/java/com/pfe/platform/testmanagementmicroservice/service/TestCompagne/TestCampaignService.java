package com.pfe.platform.testmanagementmicroservice.service.TestCompagne;

import com.pfe.platform.testmanagementmicroservice.entity.TestCampaign;

import java.util.List;

public interface TestCampaignService {
    List<TestCampaign> findAll();

    List<TestCampaign> findByProject(Long projectId);

    TestCampaign findById(Long id);

    TestCampaign create(Long projectId, TestCampaign campaign);

    TestCampaign update(Long id, TestCampaign incoming);

    void delete(Long id);
}

