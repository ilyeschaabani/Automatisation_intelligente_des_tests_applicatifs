package com.pfe.platform.testmanagementmicroservice.service.TestExecusion;

import com.pfe.platform.testmanagementmicroservice.entity.TestCampaign;
import com.pfe.platform.testmanagementmicroservice.entity.TestCase;
import com.pfe.platform.testmanagementmicroservice.entity.TestExecution;
import com.pfe.platform.testmanagementmicroservice.repository.TestCampaignRepository;
import com.pfe.platform.testmanagementmicroservice.repository.TestCaseRepository;
import com.pfe.platform.testmanagementmicroservice.repository.TestExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TestExecutionServiceImpl implements TestExecutionService {

    private final TestExecutionRepository testExecutionRepository;
    private final TestCampaignRepository testCampaignRepository;
    private final TestCaseRepository testCaseRepository;

    @Override
    public List<TestExecution> findAll() {
        return testExecutionRepository.findAll();
    }

    @Override
    public List<TestExecution> findByCampaign(Long campaignId) {
        return testExecutionRepository.findByCampaignId(campaignId);
    }

    @Override
    public List<TestExecution> findByTestCase(Long testCaseId) {
        return testExecutionRepository.findByTestCaseId(testCaseId);
    }

    @Override
    public TestExecution findById(Long id) {
        return testExecutionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + id));
    }

    @Override
    public TestExecution create(Long campaignId, Long testCaseId, TestExecution exec) {
        TestCampaign campaign = testCampaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));
        TestCase testCase = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new IllegalArgumentException("TestCase not found: " + testCaseId));

        exec.setId(null);
        exec.setCampaign(campaign);
        exec.setTestCase(testCase);
        if (exec.getExecutedAt() == null) {
            exec.setExecutedAt(Instant.now());
        }
        return testExecutionRepository.save(exec);
    }

    @Override
    public TestExecution update(Long id, TestExecution incoming) {
        TestExecution existing = findById(id);
        existing.setStatus(incoming.getStatus());
        existing.setDuration(incoming.getDuration());
        existing.setExecutedAt(incoming.getExecutedAt());
        existing.setErrorMessage(incoming.getErrorMessage());
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
