package com.pfe.platform.testmanagementmicroservice.repository;

import com.pfe.platform.testmanagementmicroservice.entity.TestSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestSessionRepository extends JpaRepository<TestSession, Long> {
    List<TestSession> findByProjectId(Long projectId);
    List<TestSession> findByCampaignId(Long campaignId);
}

