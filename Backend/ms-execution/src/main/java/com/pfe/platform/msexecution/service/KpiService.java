package com.pfe.platform.msexecution.service;

import com.pfe.platform.msexecution.dto.KpiResponse;
import com.pfe.platform.msexecution.dto.TrendPoint;
import com.pfe.platform.msexecution.entity.Campaign;
import com.pfe.platform.msexecution.entity.ExecutionResult;
import com.pfe.platform.msexecution.entity.TestCase;
import com.pfe.platform.msexecution.repository.CampaignRepository;
import com.pfe.platform.msexecution.repository.ExecutionResultRepository;
import com.pfe.platform.msexecution.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KpiService {

    private static final int DEFAULT_CAMPAIGN_WINDOW = 5;

    private final CampaignRepository campaignRepository;
    private final ExecutionResultRepository executionResultRepository;
    private final TestCaseRepository testCaseRepository;

    public KpiResponse getKpi(Long projectId) {
        List<Campaign> campaigns = campaignRepository.findByProjectIdOrderByStartedAtDesc(projectId);
        if (campaigns.isEmpty()) {
            return emptyResponse();
        }

        List<Campaign> latestCampaigns = campaigns.stream().limit(DEFAULT_CAMPAIGN_WINDOW).toList();
        Set<Long> latestCampaignIds = latestCampaigns.stream().map(Campaign::getId).collect(Collectors.toSet());
        List<Long> allCampaignIds = campaigns.stream().map(Campaign::getId).toList();

        List<ExecutionResult> allResults = allCampaignIds.isEmpty()
                ? Collections.emptyList()
                : executionResultRepository.findByCampaignIdIn(allCampaignIds);
        List<ExecutionResult> latestResults = allResults.stream()
                .filter(result -> latestCampaignIds.contains(result.getCampaignId()))
                .toList();

        // All headline cards share the same scope (full project history) so the numbers
        // reconcile: passed + failed (+ untracked) = totalTestsRun.
        long totalTestsRun = allResults.size();
        long passedTests = allResults.stream().filter(this::isSuccess).count();
        long failedTests = allResults.stream().filter(this::isFailure).count();
        double successRate = totalTestsRun == 0 ? 0.0 : percentage(passedTests, totalTestsRun);
        long activeCampaigns = campaignRepository.countByProjectIdAndStatus(projectId, Campaign.CampaignStatus.RUNNING);

        // Flaky = a result that needed at least one retry (retryCount > 0).
        long flakyTests = allResults.stream()
                .filter(result -> result.getRetryCount() != null && result.getRetryCount() > 0)
                .count();

        // Average per-test duration across every timed execution in the project.
        double averageDurationMs = allResults.stream()
                .map(ExecutionResult::getDurationMs)
                .filter(duration -> duration != null && duration > 0)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);

        String totalExecutionTime = resolveLastFinishedCampaignDuration(projectId, campaigns, allResults);
        List<KpiResponse.TestMetric> top5SlowestTests = buildTopSlowestTests(allResults);
        List<KpiResponse.TestMetric> top5FailingTests = buildTopFailingTests(allResults);
        KpiResponse.Evolution evolution = buildEvolution(latestCampaigns, allResults);

        return KpiResponse.builder()
                .totalTestsRun(totalTestsRun)
                .passedTests(passedTests)
                .successRate(successRate)
                .failedTests(failedTests)
                .activeCampaigns(activeCampaigns)
                .totalExecutionTime(totalExecutionTime)
                .flakyTests(flakyTests)
                .averageDurationMs(averageDurationMs > 0 ? round(averageDurationMs) : 0.0)
                .averageDuration(formatDuration(Math.round(averageDurationMs)))
                .top5SlowestTests(top5SlowestTests)
                .top5FailingTests(top5FailingTests)
                .evolution(evolution)
                .build();
    }

    public List<TrendPoint> getTrend(Long projectId) {
        List<Campaign> campaigns = campaignRepository.findByProjectIdOrderByStartedAtDesc(projectId);
        if (campaigns.isEmpty()) {
            return Collections.emptyList();
        }

        // Return all campaigns in chronological order instead of limiting to last 4
        List<Campaign> allCampaigns = new ArrayList<>(campaigns);
        Collections.reverse(allCampaigns);

        List<Long> campaignIds = allCampaigns.stream().map(Campaign::getId).toList();
        List<ExecutionResult> results = campaignIds.isEmpty()
                ? Collections.emptyList()
                : executionResultRepository.findByCampaignIdIn(campaignIds);

        Map<Long, List<ExecutionResult>> resultsByCampaign = results.stream()
                .collect(Collectors.groupingBy(ExecutionResult::getCampaignId));

        return allCampaigns.stream()
                .map(campaign -> {
                    List<ExecutionResult> campaignResults = resultsByCampaign.getOrDefault(campaign.getId(), Collections.emptyList());
                    int passed = (int) campaignResults.stream().filter(this::isSuccess).count();
                    int failed = (int) campaignResults.stream().filter(this::isFailure).count();
                    int skipped = 0;
                    return TrendPoint.builder()
                            .label("Campaign #" + campaign.getId())
                            .passed(passed)
                            .failed(failed)
                            .skipped(skipped)
                            .build();
                })
                .toList();
    }

    private KpiResponse emptyResponse() {
        return KpiResponse.builder()
                .totalTestsRun(0L)
                .passedTests(0L)
                .successRate(0.0)
                .failedTests(0L)
                .activeCampaigns(0L)
                .totalExecutionTime("0 ms")
                .flakyTests(0L)
                .averageDurationMs(0.0)
                .averageDuration("0 ms")
                .top5SlowestTests(Collections.emptyList())
                .top5FailingTests(Collections.emptyList())
                .evolution(KpiResponse.Evolution.builder()
                        .previousRate(0.0)
                        .currentRate(0.0)
                        .trend("STABLE")
                        .build())
                .build();
    }

    private String resolveLastFinishedCampaignDuration(Long projectId, List<Campaign> campaigns, List<ExecutionResult> allResults) {
        List<Campaign> finishedCampaigns = campaignRepository.findByProjectIdAndStatusInOrderByFinishedAtDesc(
                projectId,
                List.of(Campaign.CampaignStatus.FINISHED, Campaign.CampaignStatus.FINISHED_WITH_ERRORS)
        );

        Campaign lastFinished = finishedCampaigns.isEmpty() ? null : finishedCampaigns.get(0);
        if (lastFinished == null) {
            return "0 ms";
        }

        long totalDuration = allResults.stream()
                .filter(result -> lastFinished.getId().equals(result.getCampaignId()))
                .map(ExecutionResult::getDurationMs)
                .filter(duration -> duration != null && duration > 0)
                .mapToLong(Long::longValue)
                .sum();

        return formatDuration(totalDuration);
    }

    private List<KpiResponse.TestMetric> buildTopSlowestTests(List<ExecutionResult> allResults) {
        if (allResults.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<Long>> durationsByTestCase = allResults.stream()
                .filter(result -> result.getTestCaseId() != null && result.getDurationMs() != null)
                .collect(Collectors.groupingBy(
                        ExecutionResult::getTestCaseId,
                        Collectors.mapping(ExecutionResult::getDurationMs, Collectors.toList())
                ));

        return buildTopMetrics(durationsByTestCase, true);
    }

    private List<KpiResponse.TestMetric> buildTopFailingTests(List<ExecutionResult> latestResults) {
        if (latestResults.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<Long>> failuresByTestCase = latestResults.stream()
                .filter(result -> result.getTestCaseId() != null && isFailure(result))
                .collect(Collectors.groupingBy(
                        ExecutionResult::getTestCaseId,
                        Collectors.mapping(result -> 1L, Collectors.toList())
                ));

        return buildTopMetrics(failuresByTestCase, false);
    }

    private List<KpiResponse.TestMetric> buildTopMetrics(Map<Long, List<Long>> valuesByTestCase, boolean useAverageDuration) {
        if (valuesByTestCase.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> orderedTestCaseIds = valuesByTestCase.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), score(entry.getValue(), useAverageDuration)))
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        Map<Long, TestCase> testCasesById = testCaseRepository.findAllById(orderedTestCaseIds).stream()
                .collect(Collectors.toMap(TestCase::getId, testCase -> testCase));

        List<KpiResponse.TestMetric> metrics = new ArrayList<>();
        for (Long testCaseId : orderedTestCaseIds) {
            List<Long> values = valuesByTestCase.getOrDefault(testCaseId, Collections.emptyList());
            TestCase testCase = testCasesById.get(testCaseId);
            String label = resolveTestCaseLabel(testCaseId, testCase);

            if (useAverageDuration) {
                double average = values.stream().mapToLong(Long::longValue).average().orElse(0.0);
                metrics.add(KpiResponse.TestMetric.builder()
                        .testCaseId(testCaseId)
                        .testCaseLabel(label)
                        .averageDurationMs(round(average))
                        .averageDuration(formatDuration(Math.round(average)))
                        .failureCount(0L)
                        .build());
            } else {
                metrics.add(KpiResponse.TestMetric.builder()
                        .testCaseId(testCaseId)
                        .testCaseLabel(label)
                        .averageDurationMs(null)
                        .averageDuration(null)
                        .failureCount((long) values.size())
                        .build());
            }
        }

        return metrics;
    }

    private double score(List<Long> values, boolean average) {
        if (values.isEmpty()) {
            return 0.0;
        }
        if (average) {
            return values.stream().mapToLong(Long::longValue).average().orElse(0.0);
        }
        return values.size();
    }

    private KpiResponse.Evolution buildEvolution(List<Campaign> latestCampaigns, List<ExecutionResult> allResults) {
        if (latestCampaigns.isEmpty()) {
            return KpiResponse.Evolution.builder()
                    .previousRate(0.0)
                    .currentRate(0.0)
                    .trend("STABLE")
                    .build();
        }

        Campaign currentCampaign = latestCampaigns.get(0);
        Campaign previousCampaign = latestCampaigns.size() > 1 ? latestCampaigns.get(1) : null;

        double currentRate = campaignSuccessRate(currentCampaign, allResults);
        double previousRate = previousCampaign != null ? campaignSuccessRate(previousCampaign, allResults) : 0.0;

        String trend;
        if (Math.abs(currentRate - previousRate) < 0.01) {
            trend = "STABLE";
        } else if (currentRate > previousRate) {
            trend = "UP";
        } else {
            trend = "DOWN";
        }

        return KpiResponse.Evolution.builder()
                .previousRate(previousRate)
                .currentRate(currentRate)
                .trend(trend)
                .build();
    }

    private double campaignSuccessRate(Campaign campaign, List<ExecutionResult> allResults) {
        List<ExecutionResult> campaignResults = allResults.stream()
                .filter(result -> campaign.getId().equals(result.getCampaignId()))
                .toList();
        long total = campaignResults.size();
        if (total == 0) {
            return 0.0;
        }
        long success = campaignResults.stream().filter(this::isSuccess).count();
        return percentage(success, total);
    }

    private boolean isSuccess(ExecutionResult result) {
        return result.getStatus() == ExecutionResult.ResultStatus.SUCCESS;
    }

    private boolean isFailure(ExecutionResult result) {
        return result.getStatus() == ExecutionResult.ResultStatus.FAILURE
                || result.getStatus() == ExecutionResult.ResultStatus.ERROR;
    }

    private String resolveTestCaseLabel(Long testCaseId, TestCase testCase) {
        if (testCase == null) {
            return "TestCase #" + testCaseId;
        }
        if (testCase.getScriptPath() != null && !testCase.getScriptPath().isBlank()) {
            return testCase.getScriptPath();
        }
        return "TestCase #" + testCase.getId();
    }

    private double percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return round((numerator * 100.0d) / denominator);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String formatDuration(long durationMs) {
        if (durationMs <= 0) {
            return "0 ms";
        }

        Duration duration = Duration.ofMillis(durationMs);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        long millis = duration.toMillisPart();

        List<String> parts = new ArrayList<>();
        if (hours > 0) parts.add(hours + "h");
        if (minutes > 0) parts.add(minutes + "m");
        if (seconds > 0) parts.add(seconds + "s");
        if (parts.isEmpty() || millis > 0) parts.add(millis + "ms");
        return String.join(" ", parts);
    }
}