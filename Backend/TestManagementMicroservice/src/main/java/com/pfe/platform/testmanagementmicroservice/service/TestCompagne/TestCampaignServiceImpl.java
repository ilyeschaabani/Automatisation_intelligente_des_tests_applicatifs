package com.pfe.platform.testmanagementmicroservice.service.TestCompagne;

import com.pfe.platform.testmanagementmicroservice.DTO.TestCampaignCreateRequest;
import com.pfe.platform.testmanagementmicroservice.DTO.TestCampaignSetTestCasesRequest;
import com.pfe.platform.testmanagementmicroservice.DTO.TestCampaignUpdateRequest;
import com.pfe.platform.testmanagementmicroservice.entity.Project;
import com.pfe.platform.testmanagementmicroservice.entity.TestCampaign;
import com.pfe.platform.testmanagementmicroservice.entity.TestCase;
import com.pfe.platform.testmanagementmicroservice.repository.ProjectRepository;
import com.pfe.platform.testmanagementmicroservice.repository.TestCampaignRepository;
import com.pfe.platform.testmanagementmicroservice.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TestCampaignServiceImpl implements TestCampaignService {

    private final TestCampaignRepository testCampaignRepository;
    private final ProjectRepository projectRepository;
    private final TestCaseRepository testCaseRepository;

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
        return testCampaignRepository.findWithTestCasesById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + id));
    }

    @Override
    public TestCampaign create(TestCampaignCreateRequest request) {
        if (request == null || request.projectId() == null) {
            throw new IllegalArgumentException("projectId is required to create a campaign");
        }

        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + request.projectId()));

        TestCampaign campaign = new TestCampaign();
        campaign.setId(null);
        campaign.setProject(project);

        campaign.setName(request.name());
        campaign.setVersion(request.version());
        campaign.setStatus(request.status());
        campaign.setStartDate(request.startDate());
        campaign.setEndDate(request.endDate());

        campaign.setEnvironment(request.environment());
        campaign.setTriggerType(request.triggerType());
        if (request.sessionStatus() != null) {
            campaign.setSessionStatus(request.sessionStatus());
        }
        campaign.setExecutionStartDate(request.executionStartDate());
        campaign.setExecutionEndDate(request.executionEndDate());

        campaign.setCreatedBy(request.createdBy());

        return testCampaignRepository.save(campaign);
    }

    @Override
    public TestCampaign update(Long id, TestCampaignUpdateRequest request) {
        TestCampaign existing = findById(id);

        existing.setName(request.name());
        existing.setVersion(request.version());
        existing.setStatus(request.status());
        existing.setStartDate(request.startDate());
        existing.setEndDate(request.endDate());

        existing.setEnvironment(request.environment());
        existing.setTriggerType(request.triggerType());
        if (request.sessionStatus() != null) {
            existing.setSessionStatus(request.sessionStatus());
        }
        existing.setExecutionStartDate(request.executionStartDate());
        existing.setExecutionEndDate(request.executionEndDate());

        existing.setCreatedBy(request.createdBy());

        return testCampaignRepository.save(existing);
    }

    @Override
    public TestCampaign setTestCases(Long id, TestCampaignSetTestCasesRequest request) {
        TestCampaign campaign = findById(id);

        List<Long> ids = (request == null || request.testCaseIds() == null) ? List.of() : request.testCaseIds();
        List<TestCase> cases = ids.isEmpty() ? List.of() : testCaseRepository.findAllById(ids);

        // Ensure all IDs exist
        if (cases.size() != ids.size()) {
            Set<Long> found = new HashSet<>(cases.stream().map(TestCase::getId).toList());
            List<Long> missing = ids.stream().filter(x -> !found.contains(x)).toList();
            throw new IllegalArgumentException("TestCase(s) not found: " + missing);
        }

        campaign.getTestCases().clear();
        campaign.getTestCases().addAll(cases);

        return testCampaignRepository.save(campaign);
    }

    @Override
    public void delete(Long id) {
        if (!testCampaignRepository.existsById(id)) {
            throw new IllegalArgumentException("Campaign not found: " + id);
        }
        testCampaignRepository.deleteById(id);
    }
}
