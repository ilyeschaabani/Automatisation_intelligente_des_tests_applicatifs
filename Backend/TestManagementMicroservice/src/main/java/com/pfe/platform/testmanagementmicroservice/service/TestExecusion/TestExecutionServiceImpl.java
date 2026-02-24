package com.pfe.platform.testmanagementmicroservice.service.TestExecusion;

import com.pfe.platform.testmanagementmicroservice.entity.TestExecution;
import com.pfe.platform.testmanagementmicroservice.entity.TestSession;
import com.pfe.platform.testmanagementmicroservice.repository.TestExecutionRepository;
import com.pfe.platform.testmanagementmicroservice.repository.TestSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestExecutionServiceImpl implements TestExecutionService {

    private final TestExecutionRepository testExecutionRepository;
    private final TestSessionRepository testSessionRepository;

    @Override
    public List<TestExecution> findAll() {
        return testExecutionRepository.findAll();
    }

    @Override
    public List<TestExecution> findBySession(Long sessionId) {
        return testExecutionRepository.findBySessionId(sessionId);
    }

    @Override
    public TestExecution findById(Long id) {
        return testExecutionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + id));
    }

    @Override
    public TestExecution createBySession(Long sessionId, TestExecution exec) {
        TestSession session = testSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        exec.setId(null);
        exec.setSession(session);
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
