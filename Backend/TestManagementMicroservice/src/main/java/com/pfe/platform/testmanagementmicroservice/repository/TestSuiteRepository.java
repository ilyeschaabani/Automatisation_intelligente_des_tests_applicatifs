package com.pfe.platform.testmanagementmicroservice.repository;

import com.pfe.platform.testmanagementmicroservice.entity.TestSuite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestSuiteRepository extends JpaRepository<TestSuite, Long> {
    List<TestSuite> findByProjectId(Long projectId);
}

