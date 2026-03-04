package com.pfe.platform.testmanagementmicroservice.service.TestCase;

import com.pfe.platform.testmanagementmicroservice.entity.TestCase;

import java.util.List;

public interface TestCaseService {
    List<TestCase> findAll();

    TestCase findById(Long id);

    TestCase create(TestCase testCase);

    TestCase update(Long id, TestCase incoming);

    void delete(Long id);
}
