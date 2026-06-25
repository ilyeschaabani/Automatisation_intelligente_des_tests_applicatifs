package com.pfe.platform.msexecution.repository;

import com.pfe.platform.msexecution.entity.EvaluationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluationMemberRepository extends JpaRepository<EvaluationMember, Long> {
    boolean existsByEvaluationIdAndUserId(Long evaluationId, Long userId);
    List<EvaluationMember> findByEvaluationId(Long evaluationId);
    List<EvaluationMember> findByUserId(Long userId);
    void deleteByEvaluationIdAndUserId(Long evaluationId, Long userId);
}
