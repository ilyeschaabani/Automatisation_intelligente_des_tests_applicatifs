package com.pfe.platform.msexecution.repository;

import com.pfe.platform.msexecution.entity.UxEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UxEvaluationRepository extends JpaRepository<UxEvaluation, Long> {
    List<UxEvaluation> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<UxEvaluation> findByProjectIdAndPlatformOrderByCreatedAtDesc(Long projectId, UxEvaluation.Platform platform);
    List<UxEvaluation> findAllByOrderByCreatedAtDesc();
    List<UxEvaluation> findByPlatformOrderByCreatedAtDesc(UxEvaluation.Platform platform);
}
