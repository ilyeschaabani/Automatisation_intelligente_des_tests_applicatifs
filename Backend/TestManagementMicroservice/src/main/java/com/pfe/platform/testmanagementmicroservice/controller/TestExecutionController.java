package com.pfe.platform.testmanagementmicroservice.controller;

import com.pfe.platform.testmanagementmicroservice.DTO.TestExecutionDto;
import com.pfe.platform.testmanagementmicroservice.entity.TestExecution;
import com.pfe.platform.testmanagementmicroservice.service.TestExecusion.TestExecutionMapper;
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
    public List<TestExecutionDto> getAll(@RequestParam(name = "campaignId", required = false) Long campaignId) {
        List<TestExecution> executions = (campaignId != null)
                ? testExecutionService.findByCampaign(campaignId)
                : testExecutionService.findAll();

        return executions.stream().map(TestExecutionMapper::toDto).toList();
    }

    @GetMapping("/{id}")
    public TestExecutionDto getById(@PathVariable Long id) {
        return TestExecutionMapper.toDto(testExecutionService.findById(id));
    }

    @PostMapping
    public ResponseEntity<TestExecutionDto> create(@RequestParam Long campaignId,
                                                  @RequestBody TestExecution execution) {
        TestExecution created = testExecutionService.createByCampaign(campaignId, execution);
        return ResponseEntity.status(HttpStatus.CREATED).body(TestExecutionMapper.toDto(created));
    }

    @PutMapping("/{id}")
    public TestExecutionDto update(@PathVariable Long id, @RequestBody TestExecution execution) {
        return TestExecutionMapper.toDto(testExecutionService.update(id, execution));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        testExecutionService.delete(id);
    }
}
