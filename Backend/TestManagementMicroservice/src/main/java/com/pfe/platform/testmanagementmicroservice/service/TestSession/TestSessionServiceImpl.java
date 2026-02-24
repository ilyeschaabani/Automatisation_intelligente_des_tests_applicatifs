package com.pfe.platform.testmanagementmicroservice.service.TestSession;

import com.pfe.platform.testmanagementmicroservice.entity.Project;
import com.pfe.platform.testmanagementmicroservice.entity.TestCampaign;
import com.pfe.platform.testmanagementmicroservice.entity.TestSession;
import com.pfe.platform.testmanagementmicroservice.repository.ProjectRepository;
import com.pfe.platform.testmanagementmicroservice.repository.TestCampaignRepository;
import com.pfe.platform.testmanagementmicroservice.repository.TestSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestSessionServiceImpl implements TestSessionService {

    private final TestSessionRepository testSessionRepository;
    private final ProjectRepository projectRepository;
    private final TestCampaignRepository testCampaignRepository;

    @Override
    public List<TestSession> findAll() {
        return testSessionRepository.findAll();
    }

    @Override
    public List<TestSession> findByProject(Long projectId) {
        return testSessionRepository.findByProjectId(projectId);
    }

    @Override
    public List<TestSession> findByCampaign(Long campaignId) {
        return testSessionRepository.findByCampaignId(campaignId);
    }

    @Override
    public TestSession findById(Long id) {
        return testSessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TestSession not found: " + id));
    }

    @Override
    public TestSession create(Long projectId, Long campaignId, TestSession session) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        session.setId(null);
        session.setProject(project);

        if (campaignId != null) {
            TestCampaign campaign = testCampaignRepository.findById(campaignId)
                    .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));
            session.setCampaign(campaign);
        } else {
            session.setCampaign(null);
        }

        return testSessionRepository.save(session);
    }

    @Override
    public TestSession update(Long id, TestSession incoming) {
        TestSession existing = findById(id);

        existing.setStartDate(incoming.getStartDate());
        existing.setEndDate(incoming.getEndDate());
        existing.setEnvironment(incoming.getEnvironment());
        existing.setStatus(incoming.getStatus());
        existing.setTriggerType(incoming.getTriggerType());

        // project is immutable here (use delete+create if you want to move)

        // campaign can be changed if provided in incoming
        existing.setCampaign(incoming.getCampaign());

        return testSessionRepository.save(existing);
    }

    @Override
    public void delete(Long id) {
        if (!testSessionRepository.existsById(id)) {
            throw new IllegalArgumentException("TestSession not found: " + id);
        }
        testSessionRepository.deleteById(id);
    }
}

