package com.pfe.platform.testmanagementmicroservice.controller;

import com.pfe.platform.testmanagementmicroservice.DTO.*;
import com.pfe.platform.testmanagementmicroservice.entity.TestCampaign;
import com.pfe.platform.testmanagementmicroservice.service.TestCompagne.CampaignRunService;
import com.pfe.platform.testmanagementmicroservice.service.TestCompagne.TestCampaignMapper;
import com.pfe.platform.testmanagementmicroservice.service.TestCompagne.TestCampaignService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/campaigns")
public class TestCampaignController {

    private final TestCampaignService testCampaignService;
    private final CampaignRunService campaignRunService;

    public TestCampaignController(TestCampaignService testCampaignService , CampaignRunService campaignRunService) {
        this.testCampaignService = testCampaignService;
        this.campaignRunService = campaignRunService;
    }

    @GetMapping
    public List<TestCampaignDto> getAll(@RequestParam(name = "projectId", required = false) Long projectId) {
        List<TestCampaign> campaigns = (projectId == null)
                ? testCampaignService.findAll()
                : testCampaignService.findByProject(projectId);

        return campaigns.stream().map(TestCampaignMapper::toDto).toList();
    }

    @GetMapping("/{id}")
    public TestCampaignDto getById(@PathVariable Long id) {
        return TestCampaignMapper.toDto(testCampaignService.findById(id));
    }

    @PostMapping
    public ResponseEntity<TestCampaignDto> create(@RequestBody TestCampaignCreateRequest request) {
        TestCampaign created = testCampaignService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TestCampaignMapper.toDto(created));
    }

    @PutMapping("/{id}")
    public TestCampaignDto update(@PathVariable Long id, @RequestBody TestCampaignUpdateRequest request) {
        return TestCampaignMapper.toDto(testCampaignService.update(id, request));
    }

    @PutMapping("/{id}/testcases")
    public TestCampaignDto setTestCases(@PathVariable Long id,
                                        @RequestBody TestCampaignSetTestCasesRequest request) {
        return TestCampaignMapper.toDto(testCampaignService.setTestCases(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        testCampaignService.delete(id);
    }

    @GetMapping("/{campaignId}/endpoints")
    public ResponseEntity<List<EndpointDto>> getEndpointsForTestCampaign(@PathVariable Long campaignId) {
        List<EndpointDto> endpoints = testCampaignService.getEndpointsForTestCampaign(campaignId);
        return ResponseEntity.ok(endpoints);
    }

    @PostMapping("/{campaignId}/run")
    public ResponseEntity<CampaignRunResponse> runCampaign(
            @PathVariable Long campaignId,
            @RequestBody(required = false) CampaignRunRequest request
    ) {
        return campaignRunService.run(campaignId, request);
    }

    @PostMapping("/{campaignId}/run/continue")
    public ResponseEntity<CampaignRunResponse> continueCampaignRun(
            @PathVariable Long campaignId,
            @RequestBody CampaignRunContinueRequest request
    ) {
        return campaignRunService.continueRun(campaignId, request);
    }

    @PostMapping("/{campaignId}/run/{executionId}/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupCampaignRun(
            @PathVariable Long campaignId,
            @PathVariable Long executionId
    ) {
        campaignRunService.cleanupRun(campaignId, executionId);
        return ResponseEntity.ok(Map.of("status", "cleaned"));
    }

}
