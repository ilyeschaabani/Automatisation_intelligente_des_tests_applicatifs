package com.pfe.platform.testmanagementmicroservice.service.TestCase;

import com.pfe.platform.testmanagementmicroservice.entity.TestCase;
import com.pfe.platform.testmanagementmicroservice.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestCaseServiceImpl implements TestCaseService {

    private final TestCaseRepository testCaseRepository;

    @Override
    public List<TestCase> findAll() {
        return testCaseRepository.findAll();
    }

    @Override
    public TestCase findById(Long id) {
        return testCaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TestCase not found: " + id));
    }

    @Override
    public TestCase create(TestCase testCase) {
        testCase.setId(null);
        return testCaseRepository.save(testCase);
    }

    @Override
    public TestCase update(Long id, TestCase incoming) {
        TestCase existing = findById(id);
        existing.setName(incoming.getName());
        existing.setDescription(incoming.getDescription());
        existing.setTestType(incoming.getTestType());
        existing.setPriority(incoming.getPriority());
        existing.setTool(incoming.getTool());
        existing.setRiskScore(incoming.getRiskScore());
        return testCaseRepository.save(existing);
    }

    @Override
    public void delete(Long id) {
        if (!testCaseRepository.existsById(id)) {
            throw new IllegalArgumentException("TestCase not found: " + id);
        }
        testCaseRepository.deleteById(id);
    }
}
