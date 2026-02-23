package com.pfe.platform.testmanagementmicroservice.controller;

import com.pfe.platform.testmanagementmicroservice.entity.TestCampaign;
import com.pfe.platform.testmanagementmicroservice.service.TestCompagne.TestCampaignService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/campaigns")
public class TestCampaignController {

    private final TestCampaignService testCampaignService;

    public TestCampaignController(TestCampaignService testCampaignService) {
        this.testCampaignService = testCampaignService;
    }

    @GetMapping
    public List<TestCampaign> getAll(@RequestParam(name = "projectId", required = false) Long projectId) {
        return (projectId == null) ? testCampaignService.findAll() : testCampaignService.findByProject(projectId);
    }

    @GetMapping("/{id}")
    public TestCampaign getById(@PathVariable Long id) {
        return testCampaignService.findById(id);
    }

    @PostMapping
    public ResponseEntity<TestCampaign> create(@RequestParam Long projectId, @RequestBody TestCampaign campaign) {
        TestCampaign created = testCampaignService.create(projectId, campaign);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public TestCampaign update(@PathVariable Long id, @RequestBody TestCampaign campaign) {
        return testCampaignService.update(id, campaign);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        testCampaignService.delete(id);
    }
}
