package com.pfe.platform.testmanagementmicroservice.service.TestSuite;

import com.pfe.platform.testmanagementmicroservice.entity.TestSuite;

import java.util.List;

public interface TestSuiteService {
    List<TestSuite> findAll();

    List<TestSuite> findByProject(Long projectId);

    TestSuite findById(Long id);

    TestSuite create(Long projectId, TestSuite suite);

    TestSuite update(Long id, TestSuite incoming);

    void delete(Long id);
}

