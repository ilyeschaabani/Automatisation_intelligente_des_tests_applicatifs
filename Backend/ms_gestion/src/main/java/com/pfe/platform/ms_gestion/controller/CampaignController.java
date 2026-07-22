package com.pfe.platform.ms_gestion.controller;

import com.pfe.platform.ms_gestion.dto.request.CreateCampaignRequest;
import com.pfe.platform.ms_gestion.dto.response.CampaignResponse;
import com.pfe.platform.ms_gestion.dto.response.TestCaseWithStatusResponse;
import com.pfe.platform.ms_gestion.service.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/campaigns")
@RequiredArgsConstructor

public class CampaignController {
    private final CampaignService campaignService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER')")
    public ResponseEntity<CampaignResponse> create(@PathVariable Long projectId,
                                                   @Valid @RequestBody CreateCampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.create(projectId, request));
    }

    @PutMapping("/{campaignId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'TESTEUR')")
    public ResponseEntity<CampaignResponse> update(@PathVariable Long projectId,
                                                   @PathVariable Long campaignId,
                                                   @Valid @RequestBody CreateCampaignRequest request) {
        return ResponseEntity.ok(campaignService.update(projectId, campaignId, request));
    }

    @GetMapping
    public ResponseEntity<List<CampaignResponse>> list(@PathVariable Long projectId) {
        return ResponseEntity.ok(campaignService.listForProject(projectId));
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> get(@PathVariable Long projectId,
                                                @PathVariable Long campaignId) {
        return ResponseEntity.ok(campaignService.getCampaign(projectId, campaignId));
    }

    @GetMapping("/{campaignId}/testcases")
    public ResponseEntity<List<TestCaseWithStatusResponse>> getTestCases(@PathVariable Long projectId,
                                                                          @PathVariable Long campaignId) {
        return ResponseEntity.ok(campaignService.getTestCasesForCampaign(projectId, campaignId));
    }

    /** Test cases from the project NOT yet in the campaign */
    @GetMapping("/{campaignId}/available-testcases")
    public ResponseEntity<List<TestCaseWithStatusResponse>> getAvailableTestCases(
            @PathVariable Long projectId, @PathVariable Long campaignId) {
        return ResponseEntity.ok(campaignService.getAvailableTestCases(projectId, campaignId));
    }

    /** Add one or more test cases to an existing campaign */
    @PostMapping("/{campaignId}/testcases")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'TESTEUR')")
    public ResponseEntity<?> addTestCases(
            @PathVariable Long projectId,
            @PathVariable Long campaignId,
            @RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("testCaseIds");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body("testCaseIds est requis");
        }
        campaignService.addTestCasesToCampaign(projectId, campaignId, ids);
        return ResponseEntity.ok(Map.of("message", ids.size() + " cas de test ajouté(s) à la campagne"));
    }

    /** Remove a test case from an existing campaign */
    @DeleteMapping("/{campaignId}/testcases/{testCaseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'TESTEUR')")
    public ResponseEntity<?> removeTestCase(
            @PathVariable Long projectId,
            @PathVariable Long campaignId,
            @PathVariable Long testCaseId) {
        campaignService.removeTestCaseFromCampaign(projectId, campaignId, testCaseId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{campaignId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER')")
    public ResponseEntity<?> delete(@PathVariable Long projectId,
                                   @PathVariable Long campaignId) {
        campaignService.delete(projectId, campaignId);
        return ResponseEntity.noContent().build();
    }
}
