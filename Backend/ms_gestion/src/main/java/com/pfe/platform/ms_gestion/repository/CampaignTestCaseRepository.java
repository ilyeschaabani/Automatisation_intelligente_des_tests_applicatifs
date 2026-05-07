package com.pfe.platform.ms_gestion.repository;

import com.pfe.platform.ms_gestion.entity.CampaignTestCase;
import com.pfe.platform.ms_gestion.entity.CampaignTestCaseId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaignTestCaseRepository extends JpaRepository<CampaignTestCase, CampaignTestCaseId>
{
    List<CampaignTestCase> findByCampaignId(Long campaignId);
    void deleteByCampaignId(Long campaignId);
}
