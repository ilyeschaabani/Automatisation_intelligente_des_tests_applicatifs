package com.pfe.platform.msexecution.controller;

import com.pfe.platform.msexecution.dto.CampaignRunResponseDto;
import com.pfe.platform.msexecution.dto.CampaignStatusDto;
import com.pfe.platform.msexecution.dto.ExecutionResultDto;
import com.pfe.platform.msexecution.entity.Campaign;
import com.pfe.platform.msexecution.repository.CampaignRepository;
import com.pfe.platform.msexecution.repository.ExecutionResultRepository;
import com.pfe.platform.msexecution.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

            // Set status to RUNNING immediately before starting async execution
            campaign.setStatus(Campaign.CampaignStatus.RUNNING);
            campaign.setStartedAt(java.time.LocalDateTime.now());
            campaign.setProgress(5);
            campaign.setCurrentStep("Cloning repository");
            campaignRepository.save(campaign);

            // Start async execution
            executionService.runCampaign(campaignId);
            
            // Return the updated campaign status immediately
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

    @PutMapping("/stop/{campaignId}")
    public ResponseEntity<?> stopCampaign(@PathVariable Long campaignId) {
        try {
            Campaign campaign = campaignRepository.findById(campaignId)
                    .orElse(null);
            
            if (campaign == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDto("Campaign not found"));
            }

            if (campaign.getStatus() == Campaign.CampaignStatus.RUNNING) {
                campaign.setStatus(Campaign.CampaignStatus.ABORTED);
                campaign.setFinishedAt(LocalDateTime.now());
                campaignRepository.save(campaign);
                return ResponseEntity.ok(new ResponseDto("Campaign aborted successfully"));
            } else {
                return ResponseEntity.badRequest()
                        .body(new ResponseDto("Campaign is not running (current status: " + campaign.getStatus() + ")"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto("Error: " + e.getMessage()));
        }
    }

    @GetMapping("/results/{resultId}/analysis")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> getAnalysis(@PathVariable Long resultId) {
        return executionResultRepository.findById(resultId)
                .map(result -> {
                    Map<String, String> payload = new HashMap<>();
                    payload.put("analysis", result.getAiAnalysis() != null ? result.getAiAnalysis() : "");
                    return ResponseEntity.ok(payload);
                })
                .orElseGet(() -> {
                    Map<String, String> payload = new HashMap<>();
                    payload.put("message", "Execution result not found");
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(payload);
                });
    }

    @Getter
    @Setter
    public static class ResponseDto {
        private String message;
        public ResponseDto(String message) {
            this.message = message;
        }
    }

}
