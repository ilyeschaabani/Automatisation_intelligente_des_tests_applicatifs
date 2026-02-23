package com.pfe.platform.testmanagementmicroservice.controller;

import com.pfe.platform.testmanagementmicroservice.entity.TestCase;
import com.pfe.platform.testmanagementmicroservice.service.TestCase.TestCaseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cases")
public class TestCaseController {

    private final TestCaseService testCaseService;

    public TestCaseController(TestCaseService testCaseService) {
        this.testCaseService = testCaseService;
    }

    @GetMapping
    public List<TestCase> getAll(@RequestParam(name = "suiteId", required = false) Long suiteId) {
        return (suiteId == null) ? testCaseService.findAll() : testCaseService.findBySuite(suiteId);
    }

    @GetMapping("/{id}")
    public TestCase getById(@PathVariable Long id) {
        return testCaseService.findById(id);
    }

    @PostMapping
    public ResponseEntity<TestCase> create(@RequestParam Long suiteId, @RequestBody TestCase testCase) {
        TestCase created = testCaseService.create(suiteId, testCase);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public TestCase update(@PathVariable Long id, @RequestBody TestCase testCase) {
        return testCaseService.update(id, testCase);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        testCaseService.delete(id);
    }
}
