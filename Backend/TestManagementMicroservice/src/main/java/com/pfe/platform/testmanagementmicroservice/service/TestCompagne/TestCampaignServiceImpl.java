package com.pfe.platform.testmanagementmicroservice.service.TestCompagne;

import com.pfe.platform.testmanagementmicroservice.entity.Project;
import com.pfe.platform.testmanagementmicroservice.entity.TestCampaign;
import com.pfe.platform.testmanagementmicroservice.repository.ProjectRepository;
import com.pfe.platform.testmanagementmicroservice.repository.TestCampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestCampaignServiceImpl implements TestCampaignService {

    private final TestCampaignRepository testCampaignRepository;
    private final ProjectRepository projectRepository;

    @Override
    public List<TestCampaign> findAll() {
        return testCampaignRepository.findAll();
    }

    @Override
    public List<TestCampaign> findByProject(Long projectId) {
        return testCampaignRepository.findByProjectId(projectId);
    }

    @Override
    public TestCampaign findById(Long id) {
        return testCampaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + id));
    }

    @Override
    public TestCampaign create(Long projectId, TestCampaign campaign) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        campaign.setId(null);
        campaign.setProject(project);
        return testCampaignRepository.save(campaign);
    }

    @Override
    public TestCampaign update(Long id, TestCampaign incoming) {
        TestCampaign existing = findById(id);
        existing.setName(incoming.getName());
        existing.setVersion(incoming.getVersion());
        existing.setStatus(incoming.getStatus());
        existing.setStartDate(incoming.getStartDate());
        existing.setEndDate(incoming.getEndDate());
        existing.setCreatedBy(incoming.getCreatedBy());
        return testCampaignRepository.save(existing);
    }

    @Override
    public void delete(Long id) {
        if (!testCampaignRepository.existsById(id)) {
            throw new IllegalArgumentException("Campaign not found: " + id);
        }
        testCampaignRepository.deleteById(id);
    }
}

