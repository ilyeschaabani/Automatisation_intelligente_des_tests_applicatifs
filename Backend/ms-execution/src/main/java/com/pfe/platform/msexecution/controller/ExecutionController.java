package com.pfe.platform.msexecution.controller;

import com.pfe.platform.msexecution.dto.CampaignRunResponseDto;
import com.pfe.platform.msexecution.dto.CampaignStatusDto;
import com.pfe.platform.msexecution.dto.ExecutionResultDto;
import com.pfe.platform.msexecution.entity.Campaign;
import com.pfe.platform.msexecution.repository.CampaignRepository;
import com.pfe.platform.msexecution.repository.ExecutionResultRepository;
import com.pfe.platform.msexecution.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/execution")
@RequiredArgsConstructor
public class ExecutionController {
    private final ExecutionService executionService;
    private final CampaignRepository campaignRepository;
    private final ExecutionResultRepository executionResultRepository;


    @PostMapping("/run/{campaignId}")
    public ResponseEntity<CampaignRunResponseDto> runCampaign(@PathVariable Long campaignId) {
        try {
            Campaign campaign = campaignRepository.findById(campaignId)
                    .orElse(null);
            
            if (campaign == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(CampaignRunResponseDto.error("Campaign not found"));
            }

            if (campaign.getStatus() == Campaign.CampaignStatus.RUNNING) {
                List<ExecutionResultDto> results = executionResultRepository.findByCampaignId(campaignId)
                        .stream()
                        .map(ExecutionResultDto::fromEntity)
                        .toList();
                CampaignStatusDto statusDto = CampaignStatusDto.fromEntity(campaign, results);
                return ResponseEntity.ok(CampaignRunResponseDto.alreadyRunning(campaignId));
            }

            executionService.runCampaign(campaignId);
            
            List<ExecutionResultDto> results = executionResultRepository.findByCampaignId(campaignId)
                    .stream()
                    .map(ExecutionResultDto::fromEntity)
                    .toList();
            CampaignStatusDto statusDto = CampaignStatusDto.fromEntity(campaign, results);
            
            return ResponseEntity.accepted().body(CampaignRunResponseDto.started(campaignId, statusDto));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CampaignRunResponseDto.error(e.getMessage()));
        }
    }

    @GetMapping("/status/{campaignId}")
    public ResponseEntity<CampaignStatusDto> getStatus(@PathVariable Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElse(null);
        
        if (campaign == null) {
            return ResponseEntity.notFound().build();
        }

        List<ExecutionResultDto> results = executionResultRepository.findByCampaignId(campaignId)
                .stream()
                .map(ExecutionResultDto::fromEntity)
                .toList();
        
        CampaignStatusDto statusDto = CampaignStatusDto.fromEntity(campaign, results);
        return ResponseEntity.ok(statusDto);
    }

    @GetMapping("/results/{campaignId}")
    public ResponseEntity<List<ExecutionResultDto>> getExecutionResults(@PathVariable Long campaignId) {
        List<ExecutionResultDto> results = executionResultRepository.findByCampaignId(campaignId)
                .stream()
                .map(ExecutionResultDto::fromEntity)
                .toList();
        return ResponseEntity.ok(results);
    }

}
