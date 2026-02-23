package com.pfe.platform.testmanagementmicroservice.controller;

import com.pfe.platform.testmanagementmicroservice.entity.TestSuite;
import com.pfe.platform.testmanagementmicroservice.service.TestSuite.TestSuiteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suites")
public class TestSuiteController {

    private final TestSuiteService testSuiteService;

    public TestSuiteController(TestSuiteService testSuiteService) {
        this.testSuiteService = testSuiteService;
    }

    @GetMapping
    public List<TestSuite> getAll(@RequestParam(name = "projectId", required = false) Long projectId) {
        return (projectId == null) ? testSuiteService.findAll() : testSuiteService.findByProject(projectId);
    }

    @GetMapping("/{id}")
    public TestSuite getById(@PathVariable Long id) {
        return testSuiteService.findById(id);
    }

    @PostMapping
    public ResponseEntity<TestSuite> create(@RequestParam Long projectId, @RequestBody TestSuite suite) {
        TestSuite created = testSuiteService.create(projectId, suite);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public TestSuite update(@PathVariable Long id, @RequestBody TestSuite suite) {
        return testSuiteService.update(id, suite);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        testSuiteService.delete(id);
    }
}
