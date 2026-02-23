
package com.pfe.platform.testmanagementmicroservice.service.TestSuite;

import com.pfe.platform.testmanagementmicroservice.entity.Project;
import com.pfe.platform.testmanagementmicroservice.entity.TestSuite;
import com.pfe.platform.testmanagementmicroservice.repository.ProjectRepository;
import com.pfe.platform.testmanagementmicroservice.repository.TestSuiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestSuiteServiceImpl implements TestSuiteService {

    private final TestSuiteRepository testSuiteRepository;
    private final ProjectRepository projectRepository;

    @Override
    public List<TestSuite> findAll() {
        return testSuiteRepository.findAll();
    }

    @Override
    public List<TestSuite> findByProject(Long projectId) {
        return testSuiteRepository.findByProjectId(projectId);
    }

    @Override
    public TestSuite findById(Long id) {
        return testSuiteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Suite not found: " + id));
    }

    @Override
    public TestSuite create(Long projectId, TestSuite suite) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        suite.setProject(project);
        return testSuiteRepository.save(suite);
    }

    @Override
    public TestSuite update(Long id, TestSuite incoming) {
        TestSuite existing = testSuiteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Suite not found: " + id));
        existing.setName(incoming.getName());
        return testSuiteRepository.save(existing);

    }

    @Override
    public void delete(Long id) {
        TestSuite existing = testSuiteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Suite not found: " + id));
        testSuiteRepository.delete(existing);
    }
}





