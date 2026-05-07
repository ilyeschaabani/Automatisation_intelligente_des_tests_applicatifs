package com.pfe.platform.msexecution.entity;


import jakarta.persistence.*;

@Entity
@Table(name = "campaign_testcases")
@IdClass(CampaignTestCaseId.class)
public class CampaignTestCase {
    @Id
    private Long campaignId;

    @Id
    private Long testCaseId;

    @ManyToOne
    @MapsId("campaignId")
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @ManyToOne
    @MapsId("testCaseId")
    @JoinColumn(name = "test_case_id")
    private TestCase testCase;

    private Integer executionOrder;

    public CampaignTestCase() {
    }

    public CampaignTestCase(Long campaignId, Long testCaseId, Campaign campaign, TestCase testCase, Integer executionOrder) {
        this.campaignId = campaignId;
        this.testCaseId = testCaseId;
        this.campaign = campaign;
        this.testCase = testCase;
        this.executionOrder = executionOrder;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(Long campaignId) {
        this.campaignId = campaignId;
    }

    public Long getTestCaseId() {
        return testCaseId;
    }

    public void setTestCaseId(Long testCaseId) {
        this.testCaseId = testCaseId;
    }

    public Campaign getCampaign() {
        return campaign;
    }

    public void setCampaign(Campaign campaign) {
        this.campaign = campaign;
    }

    public TestCase getTestCase() {
        return testCase;
    }

    public void setTestCase(TestCase testCase) {
        this.testCase = testCase;
    }

    public Integer getExecutionOrder() {
        return executionOrder;
    }

    public void setExecutionOrder(Integer executionOrder) {
        this.executionOrder = executionOrder;
    }
}
