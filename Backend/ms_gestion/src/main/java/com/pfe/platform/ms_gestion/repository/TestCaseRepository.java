package com.pfe.platform.ms_gestion.repository;

import com.pfe.platform.ms_gestion.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    List<TestCase> findBySuiteId(Long suiteId);
    List<TestCase> findBySuiteProjectId(Long projectId);
}
