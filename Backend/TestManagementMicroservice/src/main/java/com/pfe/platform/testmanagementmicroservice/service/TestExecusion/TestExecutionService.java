package com.pfe.platform.testmanagementmicroservice.service.TestExecusion;

import com.pfe.platform.testmanagementmicroservice.entity.TestExecution;

import java.util.List;

public interface TestExecutionService {
    List<TestExecution> findAll();

    List<TestExecution> findByCampaign(Long campaignId);

    List<TestExecution> findByTestCase(Long testCaseId);

    TestExecution findById(Long id);

    TestExecution create(Long campaignId, Long testCaseId, TestExecution exec);

    TestExecution update(Long id, TestExecution incoming);

    void delete(Long id);
}

