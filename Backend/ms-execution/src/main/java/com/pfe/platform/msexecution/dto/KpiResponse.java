package com.pfe.platform.msexecution.dto;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private long totalTestsRun;
    private double successRate;
    private long failedTests;
    private long activeCampaigns;
    private String totalExecutionTime;
    private List<TestMetric> top5SlowestTests;
    private List<TestMetric> top5FailingTests;
    private Evolution evolution;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestMetric implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long testCaseId;
        private String testCaseLabel;
        private Double averageDurationMs;
        private String averageDuration;
        private Long failureCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Evolution implements Serializable {
        private static final long serialVersionUID = 1L;

        private double previousRate;
        private double currentRate;
        private String trend;
    }
}