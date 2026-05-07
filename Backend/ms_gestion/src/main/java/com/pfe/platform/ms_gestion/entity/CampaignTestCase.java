package com.pfe.platform.ms_gestion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "campaign_testcases")
@IdClass(CampaignTestCaseId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

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
}
