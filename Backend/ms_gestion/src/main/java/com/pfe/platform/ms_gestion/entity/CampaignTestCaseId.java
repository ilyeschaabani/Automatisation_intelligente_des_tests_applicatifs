package com.pfe.platform.ms_gestion.entity;

import java.util.Objects;

public class CampaignTestCaseId {
    private Long campaignId;
    private Long testCaseId;

    public CampaignTestCaseId() {}
    public CampaignTestCaseId(Long campaignId, Long testCaseId) {
        this.campaignId = campaignId;
        this.testCaseId = testCaseId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CampaignTestCaseId)) return false;
        CampaignTestCaseId that = (CampaignTestCaseId) o;
        return Objects.equals(campaignId, that.campaignId) &&
                Objects.equals(testCaseId, that.testCaseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(campaignId, testCaseId);
    }
}
