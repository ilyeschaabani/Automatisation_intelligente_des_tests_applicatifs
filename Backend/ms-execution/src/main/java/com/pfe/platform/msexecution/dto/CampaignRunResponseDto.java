package com.pfe.platform.msexecution.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class CampaignRunResponseDto {
    private String status; // "started", "already_running", "error"
    private String message;
    private Long campaignId;
    private CampaignStatusDto campaign;

    public CampaignRunResponseDto() {
    }

    public CampaignRunResponseDto(String status, String message, Long campaignId, CampaignStatusDto campaign) {
        this.status = status;
        this.message = message;
        this.campaignId = campaignId;
        this.campaign = campaign;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(Long campaignId) {
        this.campaignId = campaignId;
    }

    public CampaignStatusDto getCampaign() {
        return campaign;
    }

    public void setCampaign(CampaignStatusDto campaign) {
        this.campaign = campaign;
    }

    public static CampaignRunResponseDto started(Long campaignId, CampaignStatusDto campaign) {
        return new CampaignRunResponseDto("started", "Campaign execution started", campaignId, campaign);
    }

    public static CampaignRunResponseDto alreadyRunning(Long campaignId) {
        return new CampaignRunResponseDto("already_running", "Campaign is already running", campaignId, null);
    }

    public static CampaignRunResponseDto error(String message) {
        return new CampaignRunResponseDto("error", message, null, null);
    }
}
