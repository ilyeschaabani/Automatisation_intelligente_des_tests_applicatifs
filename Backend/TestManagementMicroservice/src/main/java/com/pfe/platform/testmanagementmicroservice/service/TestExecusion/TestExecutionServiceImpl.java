package com.pfe.platform.testmanagementmicroservice.service.TestExecusion;

import com.pfe.platform.testmanagementmicroservice.entity.TestCampaign;
import com.pfe.platform.testmanagementmicroservice.entity.TestExecution;
import com.pfe.platform.testmanagementmicroservice.repository.TestCampaignRepository;
import com.pfe.platform.testmanagementmicroservice.repository.TestExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestExecutionServiceImpl implements TestExecutionService {

    private final TestExecutionRepository testExecutionRepository;
    private final TestCampaignRepository testCampaignRepository;

    @Override
    public List<TestExecution> findAll() {
        return testExecutionRepository.findAll();
    }

    @Override
    public List<TestExecution> findByCampaign(Long campaignId) {
        return testExecutionRepository.findByCampaignId(campaignId);
    }

    @Override
    public TestExecution findById(Long id) {
        return testExecutionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + id));
    }

    @Override
    public TestExecution createByCampaign(Long campaignId, TestExecution exec) {
        TestCampaign campaign = testCampaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));
        exec.setId(null);
        exec.setCampaign(campaign);
        return testExecutionRepository.save(exec);
    }

    @Override
    public TestExecution update(Long id, TestExecution incoming) {
        TestExecution existing = findById(id);
        existing.setExecutionNumber(incoming.getExecutionNumber());
        existing.setExecutionType(incoming.getExecutionType());
        existing.setStatus(incoming.getStatus());
        existing.setExecutionDate(incoming.getExecutionDate());
        return testExecutionRepository.save(existing);
    }

    @Override
    public void delete(Long id) {
        if (!testExecutionRepository.existsById(id)) {
            throw new IllegalArgumentException("Execution not found: " + id);
        }
        testExecutionRepository.deleteById(id);
    }
}
