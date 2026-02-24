package com.pfe.platform.testmanagementmicroservice.service.TestSession;

import com.pfe.platform.testmanagementmicroservice.entity.TestSession;

import java.util.List;

public interface TestSessionService {
    List<TestSession> findAll();

    List<TestSession> findByProject(Long projectId);

    List<TestSession> findByCampaign(Long campaignId);

    TestSession findById(Long id);

    TestSession create(Long projectId, Long campaignId, TestSession session);

    TestSession update(Long id, TestSession incoming);

    void delete(Long id);
}

