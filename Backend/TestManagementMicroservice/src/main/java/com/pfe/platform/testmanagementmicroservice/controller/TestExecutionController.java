package com.pfe.platform.testmanagementmicroservice.controller;

import com.pfe.platform.testmanagementmicroservice.entity.TestExecution;
import com.pfe.platform.testmanagementmicroservice.service.TestExecusion.TestExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/executions")
public class TestExecutionController {

    private final TestExecutionService testExecutionService;

    public TestExecutionController(TestExecutionService testExecutionService) {
        this.testExecutionService = testExecutionService;
    }

    @GetMapping
    public List<TestExecution> getAll(@RequestParam(name = "campaignId", required = false) Long campaignId,
                                      @RequestParam(name = "testCaseId", required = false) Long testCaseId) {
        if (campaignId != null) {
            return testExecutionService.findByCampaign(campaignId);
        }
        if (testCaseId != null) {
            return testExecutionService.findByTestCase(testCaseId);
        }
        return testExecutionService.findAll();
    }

    @GetMapping("/{id}")
    public TestExecution getById(@PathVariable Long id) {
        return testExecutionService.findById(id);
    }

    @PostMapping
    public ResponseEntity<TestExecution> create(@RequestParam Long campaignId,
                                                @RequestParam Long testCaseId,
                                                @RequestBody TestExecution execution) {
        TestExecution created = testExecutionService.create(campaignId, testCaseId, execution);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public TestExecution update(@PathVariable Long id, @RequestBody TestExecution execution) {
        return testExecutionService.update(id, execution);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        testExecutionService.delete(id);
    }
}
