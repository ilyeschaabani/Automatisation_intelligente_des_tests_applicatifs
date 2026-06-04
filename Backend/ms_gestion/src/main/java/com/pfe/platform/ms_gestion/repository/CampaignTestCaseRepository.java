package com.pfe.platform.ms_gestion.repository;

import com.pfe.platform.ms_gestion.entity.CampaignTestCase;
import com.pfe.platform.ms_gestion.entity.CampaignTestCaseId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CampaignTestCaseRepository extends JpaRepository<CampaignTestCase, CampaignTestCaseId>
{
    List<CampaignTestCase> findByCampaignId(Long campaignId);

    boolean existsByCampaignIdAndTestCaseId(Long campaignId, Long testCaseId);

    @Modifying
    @Query(value = "DELETE FROM campaign_testcases WHERE campaign_id = :campaignId", nativeQuery = true)
    void deleteByCampaignId(@Param("campaignId") Long campaignId);

    @Modifying
    @Query(value = "DELETE FROM campaign_testcases WHERE campaign_id = :campaignId AND test_case_id = :testCaseId", nativeQuery = true)
    void deleteByCampaignIdAndTestCaseId(@Param("campaignId") Long campaignId, @Param("testCaseId") Long testCaseId);

    @Query(value = "SELECT MAX(execution_order) FROM campaign_testcases WHERE campaign_id = :campaignId", nativeQuery = true)
    Integer findMaxExecutionOrderByCampaignId(@Param("campaignId") Long campaignId);
}
