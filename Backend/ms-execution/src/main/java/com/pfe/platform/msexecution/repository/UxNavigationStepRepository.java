package com.pfe.platform.msexecution.repository;

import com.pfe.platform.msexecution.entity.UxNavigationStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UxNavigationStepRepository extends JpaRepository<UxNavigationStep, Long> {
    List<UxNavigationStep> findByEvaluationIdOrderByStepNumberAsc(Long evaluationId);
}
