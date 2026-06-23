package com.pfe.platform.msexecution.repository;

import com.pfe.platform.msexecution.entity.FunctionalTestResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FunctionalTestResultRepository extends JpaRepository<FunctionalTestResultEntity, Long> {
    List<FunctionalTestResultEntity> findByEvaluationIdOrderByIdAsc(Long evaluationId);
    void deleteByEvaluationId(Long evaluationId);
}
