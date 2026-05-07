package com.pfe.platform.msexecution.repository;

import com.pfe.platform.msexecution.entity.CampaignTestCase;
import com.pfe.platform.msexecution.entity.CampaignTestCaseId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaignTestCaseRepository extends JpaRepository<CampaignTestCase, CampaignTestCaseId> {
    List<CampaignTestCase> findByCampaignIdOrderByExecutionOrder(Long campaignId);

}