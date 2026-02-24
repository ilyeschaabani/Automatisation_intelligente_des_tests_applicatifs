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
    public List<TestExecution> getAll(@RequestParam(name = "sessionId", required = false) Long sessionId) {
        if (sessionId != null) {
            return testExecutionService.findBySession(sessionId);
        }
        return testExecutionService.findAll();
    }

    @GetMapping("/{id}")
    public TestExecution getById(@PathVariable Long id) {
        return testExecutionService.findById(id);
    }

    @PostMapping
    public ResponseEntity<TestExecution> create(@RequestParam Long sessionId,
                                               @RequestBody TestExecution execution) {
        TestExecution created = testExecutionService.createBySession(sessionId, execution);
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
