package com.pfe.platform.ms_gestion.repository;

import com.pfe.platform.ms_gestion.entity.ComplianceResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplianceResultRepository extends JpaRepository<ComplianceResult, Long> {

    List<ComplianceResult> findByProjectIdOrderByEvaluatedAtDesc(Long projectId);

    List<ComplianceResult> findAllByOrderByEvaluatedAtDesc();
}
