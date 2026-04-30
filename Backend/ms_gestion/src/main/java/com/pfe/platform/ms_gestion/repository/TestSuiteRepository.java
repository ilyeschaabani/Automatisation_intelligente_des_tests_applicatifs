package com.pfe.platform.ms_gestion.repository;

import com.pfe.platform.ms_gestion.entity.TestSuite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestSuiteRepository extends JpaRepository<TestSuite, Long> {
    List<TestSuite> findByProjectId(Long projectId);
    boolean existsByProjectIdAndName(Long projectId, String name);
}
