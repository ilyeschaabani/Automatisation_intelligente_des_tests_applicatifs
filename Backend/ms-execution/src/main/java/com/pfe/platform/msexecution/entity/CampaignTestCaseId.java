package com.pfe.platform.msexecution.entity;

import java.io.Serializable;
import java.util.Objects;

public class CampaignTestCaseId implements Serializable {
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
