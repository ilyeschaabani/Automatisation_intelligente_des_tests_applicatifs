package com.pfe.platform.testmanagementmicroservice.service.TestExecusion;

import com.pfe.platform.testmanagementmicroservice.entity.TestExecution;

import java.util.List;

public interface TestExecutionService {
    List<TestExecution> findAll();

    List<TestExecution> findByCampaign(Long campaignId);

    TestExecution findById(Long id);

    TestExecution createByCampaign(Long campaignId, TestExecution exec);

    TestExecution update(Long id, TestExecution incoming);

    void delete(Long id);
}
