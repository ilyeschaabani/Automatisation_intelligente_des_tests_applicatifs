package com.pfe.platform.msexecution.repository;

import com.pfe.platform.msexecution.entity.ExecutionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionResultRepository extends JpaRepository<ExecutionResult, Long> {
    List<ExecutionResult> findByCampaignId(Long campaignId);
}