package com.pfe.platform.ms_gestion.controller;

import com.pfe.platform.ms_gestion.dto.request.CreateCampaignRequest;
import com.pfe.platform.ms_gestion.dto.response.CampaignResponse;
import com.pfe.platform.ms_gestion.service.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/campaigns")
@RequiredArgsConstructor

public class CampaignController {
    private final CampaignService campaignService;

    @PostMapping
    public ResponseEntity<CampaignResponse> create(@PathVariable Long projectId,
                                                   @Valid @RequestBody CreateCampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.create(projectId, request));
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
}
