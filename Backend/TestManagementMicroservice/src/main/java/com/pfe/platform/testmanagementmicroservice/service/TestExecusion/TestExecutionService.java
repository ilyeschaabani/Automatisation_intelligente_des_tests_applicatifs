package com.pfe.platform.testmanagementmicroservice.service.TestExecusion;

import com.pfe.platform.testmanagementmicroservice.entity.TestExecution;

import java.util.List;

public interface TestExecutionService {
    List<TestExecution> findAll();

    List<TestExecution> findBySession(Long sessionId);

    TestExecution findById(Long id);

    TestExecution createBySession(Long sessionId, TestExecution exec);

    TestExecution update(Long id, TestExecution incoming);

    void delete(Long id);
}
