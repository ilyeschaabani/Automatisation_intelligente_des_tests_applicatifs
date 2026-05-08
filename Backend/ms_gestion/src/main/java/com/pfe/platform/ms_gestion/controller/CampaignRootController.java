package com.pfe.platform.ms_gestion.controller;

import com.pfe.platform.ms_gestion.dto.response.CampaignResponse;
import com.pfe.platform.ms_gestion.service.CampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignRootController {
    private final CampaignService campaignService;

    @GetMapping
    public ResponseEntity<List<CampaignResponse>> listAll() {
        return ResponseEntity.ok(campaignService.listAll());
    }
}
