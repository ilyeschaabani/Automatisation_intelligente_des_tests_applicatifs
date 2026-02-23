package com.pfe.platform.testmanagementmicroservice.repository;

import com.pfe.platform.testmanagementmicroservice.entity.TestCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCampaignRepository extends JpaRepository<TestCampaign, Long> {
    List<TestCampaign> findByProjectId(Long projectId);
}

