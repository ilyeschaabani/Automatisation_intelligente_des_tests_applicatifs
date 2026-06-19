package com.pfe.platform.msexecution.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.pfe.platform.msexecution.entity.Campaign;
import com.pfe.platform.msexecution.entity.CampaignTestCase;
import com.pfe.platform.msexecution.entity.Environment;
import com.pfe.platform.msexecution.entity.ExecutionResult;
import com.pfe.platform.msexecution.entity.Project;
import com.pfe.platform.msexecution.entity.TestCase;
import com.pfe.platform.msexecution.entity.TestSuite;
import com.pfe.platform.msexecution.repository.CampaignRepository;
import com.pfe.platform.msexecution.repository.CampaignTestCaseRepository;
import com.pfe.platform.msexecution.repository.ExecutionResultRepository;
import com.pfe.platform.msexecution.repository.ProjectRepository;
import com.pfe.platform.msexecution.repository.TestCaseRepository;
import com.pfe.platform.msexecution.repository.TestSuiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReportService {

    private static final String LOGO_TEXT = "TestPlatform";
    private static final int LOG_PREVIEW_CHARS = 800;
    private static final int ERROR_PREVIEW_CHARS = 400;

    private static final DeviceRgb NAVY = new DeviceRgb(0x1B, 0x2A, 0x4A);
    private static final DeviceRgb GOLD = new DeviceRgb(0xD4, 0xAF, 0x37);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(0xF5, 0xF5, 0xF5);
    private static final DeviceRgb SOFT_GRAY = new DeviceRgb(0xF8, 0xF8, 0xF8);
    private static final DeviceRgb SUCCESS = new DeviceRgb(0x1B, 0x7F, 0x4A);
    private static final DeviceRgb FAILURE = new DeviceRgb(0xC4, 0x2B, 0x2B);
    private static final DeviceRgb WARNING = new DeviceRgb(0xF0, 0x8C, 0x00);
    private static final DeviceRgb INFO = new DeviceRgb(0x22, 0x57, 0xB5);

    private final CampaignRepository campaignRepository;
    private final CampaignTestCaseRepository campaignTestCaseRepository;
    private final ExecutionResultRepository executionResultRepository;
    private final ProjectRepository projectRepository;
    private final com.pfe.platform.msexecution.repository.EnvironmentRepository environmentRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestSuiteRepository testSuiteRepository;
    private final LlmAnalysisService llmAnalysisService;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ms-gestion.service.url:http://localhost:8082}")
    private String msGestionUrl;

    public byte[] generateCampaignReport(Long campaignId, String authorizationHeader) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));

        ReportData reportData = buildReportData(campaign, authorizationHeader);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(outputStream);
            PdfDocument pdfDocument = new PdfDocument(writer);
            pdfDocument.setDefaultPageSize(PageSize.A4);
            Document document = new Document(pdfDocument, PageSize.A4);
            document.setMargins(64, 36, 54, 36);

            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont monoFont = PdfFontFactory.createFont(StandardFonts.COURIER);

            addCoverPage(pdfDocument, document, reportData, boldFont, regularFont);
            document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));

            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE,
                    new PageDecorator(reportData.generatedAt(), regularFont, boldFont));

            addExecutiveSummary(document, pdfDocument, reportData, regularFont, boldFont);
            document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));

            addContextPage(document, reportData, boldFont, regularFont);
            document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));

            addDetailedResults(document, reportData, boldFont, regularFont);
            document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));

            addAiAnalysis(document, reportData, boldFont, monoFont);

            if (hasAiCorrections(reportData)) {
                document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                addAiCorrectionsSection(document, reportData, boldFont, monoFont, regularFont);
            }

            if (hasUxTests(reportData)) {
                document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                addUxEvaluationSection(document, reportData, boldFont, monoFont);
            }
            document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));

            addMetricsSection(document, reportData, boldFont, regularFont);
            document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));

            addFlakyTests(document, reportData, boldFont, regularFont);
            document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));

            addRecommendations(document, reportData, boldFont, regularFont);

            if (!reportData.generatedScripts().isEmpty()) {
                document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                addAppendix(document, reportData, boldFont, monoFont);
            }

            document.close();
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate campaign PDF", ex);
        }
    }

    private ReportData buildReportData(Campaign campaign, String authorizationHeader) {
        ExternalCampaignInfo campaignInfo = fetchCampaignInfo(campaign.getProjectId(), campaign.getId(), authorizationHeader)
                .orElseGet(() -> ExternalCampaignInfo.fallback(campaign));
        ExternalProjectInfo projectInfo = fetchProjectInfo(campaign.getProjectId(), authorizationHeader)
                .orElseGet(() -> ExternalProjectInfo.fallback(campaign.getProjectId()));
        ExternalEnvironmentInfo environmentInfo = fetchEnvironmentInfo(campaign.getProjectId(), campaign.getEnvironmentId(), authorizationHeader)
                .orElseGet(() -> ExternalEnvironmentInfo.fallback(campaign.getEnvironmentId()));

        Environment localEnvironment = campaign.getEnvironmentId() != null
                ? environmentRepository.findById(campaign.getEnvironmentId()).orElse(null)
                : null;

        Project project = campaign.getProjectId() != null
                ? projectRepository.findById(campaign.getProjectId()).orElse(null)
                : null;

        List<CampaignTestCase> campaignTestCases = campaignTestCaseRepository.findByCampaignIdOrderByExecutionOrder(campaign.getId());
        List<ExecutionResult> executionResults = executionResultRepository.findByCampaignId(campaign.getId());

        Map<Long, ExecutionResult> resultByTestCaseId = executionResults.stream()
                .filter(result -> result.getTestCaseId() != null)
                .collect(Collectors.toMap(ExecutionResult::getTestCaseId, Function.identity(), (left, right) -> right, LinkedHashMap::new));

        Map<Long, TestCase> localTestCases = loadLocalTestCases(campaignTestCases);
        Map<Long, TestSuite> suitesById = loadSuites(localTestCases.values());
        Map<Long, ExternalTestCaseInfo> externalTestCases = loadExternalTestCases(localTestCases.values(), authorizationHeader);

        List<TestResultSummary> allSummaries = buildSummaries(campaignTestCases, localTestCases, suitesById, externalTestCases, resultByTestCaseId);
        if (allSummaries.isEmpty() && !executionResults.isEmpty()) {
            allSummaries = executionResults.stream()
                    .map(result -> toFallbackSummary(result, suitesById, externalTestCases))
                    .collect(Collectors.toList());
        }

        Map<String, List<TestResultSummary>> resultsBySuite = allSummaries.stream()
                .collect(Collectors.groupingBy(TestResultSummary::suiteName, LinkedHashMap::new, Collectors.toList()));

        List<TestResultSummary> flakyTests = allSummaries.stream()
                .filter(TestResultSummary::flaky)
                .collect(Collectors.toList());

        List<TestResultSummary> slowestTests = allSummaries.stream()
                .filter(summary -> summary.durationMs() != null)
                .sorted(Comparator.comparingLong(TestResultSummary::durationMs).reversed())
                .limit(5)
                .collect(Collectors.toList());

        Map<String, Double> averageDurationByType = allSummaries.stream()
                .filter(summary -> summary.durationMs() != null)
                .collect(Collectors.groupingBy(
                        summary -> summary.type() != null ? summary.type() : "UNKNOWN",
                        LinkedHashMap::new,
                        Collectors.averagingLong(summary -> summary.durationMs())
                ));

        int totalTests = allSummaries.size();
        int successCount = (int) allSummaries.stream().filter(summary -> "SUCCESS".equals(summary.status())).count();
        int failureCount = (int) allSummaries.stream().filter(summary -> "FAILURE".equals(summary.status())).count();
        int errorCount = (int) allSummaries.stream().filter(summary -> "ERROR".equals(summary.status())).count();
        double successRate = totalTests == 0 ? 0.0d : (successCount * 100.0d / totalTests);

        PreviousCampaignComparison comparison = resolvePreviousCampaignComparison(campaign, successRate);
        List<SuiteContext> suiteContexts = buildSuiteContext(suitesById, allSummaries, project);
        List<TestCaseContext> testCaseContexts = buildTestCaseContext(localTestCases, externalTestCases, allSummaries);
        List<GeneratedScript> generatedScripts = buildGeneratedScripts(localTestCases, externalTestCases);
        String recommendations = buildRecommendations(allSummaries);

        return new ReportData(
                campaignInfo,
                projectInfo,
                environmentInfo,
                localEnvironment,
                allSummaries,
                resultsBySuite,
                flakyTests,
                slowestTests,
                averageDurationByType,
                totalTests,
                successCount,
                failureCount,
                errorCount,
                successRate,
                comparison.label,
                comparison.previousSuccessRate,
                comparison.delta,
                comparison.displayText,
                suiteContexts,
                testCaseContexts,
                generatedScripts,
                recommendations,
                LocalDateTime.now()
        );
    }

    private List<SuiteContext> buildSuiteContext(Map<Long, TestSuite> suitesById,
                                                List<TestResultSummary> summaries,
                                                Project project) {
        List<SuiteContext> contexts = new ArrayList<>();
        for (Map.Entry<Long, TestSuite> entry : suitesById.entrySet()) {
            TestSuite suite = entry.getValue();
            String suiteName = suite != null && suite.getName() != null ? suite.getName() : "Suite #" + entry.getKey();
            String repo = suite != null ? suite.getGitRepoUrl() : null;
            String branch = suite != null ? suite.getGitBranch() : null;

            String type = summaries.stream()
                    .filter(summary -> Objects.equals(summary.suiteId(), entry.getKey()))
                    .map(TestResultSummary::type)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("UNKNOWN");

            if ((repo == null || repo.isBlank()) && project != null) {
                repo = project.getGitRepoUrl();
                branch = project.getGitDefaultBranch();
            }

            contexts.add(new SuiteContext(suiteName, type, repo, branch));
        }
        if (contexts.isEmpty()) {
            contexts.add(new SuiteContext("Suite inconnue", "UNKNOWN", null, null));
        }
        return contexts;
    }

    private List<TestCaseContext> buildTestCaseContext(Map<Long, TestCase> localTestCases,
                                                      Map<Long, ExternalTestCaseInfo> externalTestCases,
                                                      List<TestResultSummary> summaries) {
        List<TestCaseContext> contexts = new ArrayList<>();
        for (TestResultSummary summary : summaries) {
            Long id = summary.testCaseId();
            TestCase local = id != null ? localTestCases.get(id) : null;
            ExternalTestCaseInfo external = id != null ? externalTestCases.get(id) : null;

            String title = summary.testName();
            String type = summary.type();
            String priority = external != null ? external.priority : null;
            String risk = external != null ? external.riskLevel : null;
            boolean generated = local != null && Boolean.TRUE.equals(local.getGenerated());
            String scriptPath = local != null ? local.getScriptPath() : null;

            contexts.add(new TestCaseContext(id, title, type, priority, risk, generated, scriptPath));
        }
        return contexts;
    }

    private List<GeneratedScript> buildGeneratedScripts(Map<Long, TestCase> localTestCases,
                                                        Map<Long, ExternalTestCaseInfo> externalTestCases) {
        List<GeneratedScript> scripts = new ArrayList<>();
        for (TestCase testCase : localTestCases.values()) {
            if (!Boolean.TRUE.equals(testCase.getGenerated())) continue;
            if (testCase.getGeneratedCode() == null || testCase.getGeneratedCode().isBlank()) continue;

            ExternalTestCaseInfo external = externalTestCases.get(testCase.getId());
            String title = external != null && external.title != null ? external.title : "Test #" + testCase.getId();
            scripts.add(new GeneratedScript(testCase.getId(), title, testCase.getGeneratedCode()));
        }
        return scripts;
    }

    private String buildRecommendations(List<TestResultSummary> summaries) {
        List<String> fragments = new ArrayList<>();
        for (TestResultSummary summary : summaries) {
            if (!"FAILURE".equals(summary.status()) && !"ERROR".equals(summary.status())) {
                continue;
            }
            if (summary.aiAnalysis() == null || summary.aiAnalysis().isBlank()) {
                continue;
            }
            fragments.add("- " + safeValue(summary.testName()) + ":\n" + summary.aiAnalysis().trim());
        }
        return fragments.isEmpty() ? "Aucune recommandation spécifique" : String.join("\n", fragments);
    }

    private Map<Long, TestCase> loadLocalTestCases(List<CampaignTestCase> campaignTestCases) {
        List<Long> testCaseIds = campaignTestCases.stream()
                .map(CampaignTestCase::getTestCaseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (testCaseIds.isEmpty()) {
            return Map.of();
        }

        return testCaseRepository.findAllById(testCaseIds).stream()
                .collect(Collectors.toMap(TestCase::getId, Function.identity(), (left, right) -> right, LinkedHashMap::new));
    }

    private Map<Long, TestSuite> loadSuites(Iterable<TestCase> testCases) {
        List<Long> suiteIds = new ArrayList<>();
        for (TestCase testCase : testCases) {
            if (testCase.getSuiteId() != null) {
                suiteIds.add(testCase.getSuiteId());
            }
        }

        if (suiteIds.isEmpty()) {
            return Map.of();
        }

        return testSuiteRepository.findAllById(suiteIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(TestSuite::getId, Function.identity(), (left, right) -> right, LinkedHashMap::new));
    }

    private Map<Long, ExternalTestCaseInfo> loadExternalTestCases(Iterable<TestCase> testCases, String authorizationHeader) {
        Map<Long, ExternalTestCaseInfo> externalTestCases = new LinkedHashMap<>();
        Set<Long> suiteIds = new java.util.LinkedHashSet<>();
        for (TestCase testCase : testCases) {
            if (testCase.getSuiteId() != null) {
                suiteIds.add(testCase.getSuiteId());
            }
        }

        for (Long suiteId : suiteIds) {
            try {
                ResponseEntity<List<ExternalTestCaseInfo>> response = restTemplate.exchange(
                        msGestionUrl + "/api/internal/suites/" + suiteId + "/testcases",
                        HttpMethod.GET,
                        new HttpEntity<>(authorizationHeaders(authorizationHeader)),
                        new ParameterizedTypeReference<>() {}
                );

                List<ExternalTestCaseInfo> body = response.getBody();
                if (body == null) {
                    continue;
                }

                for (ExternalTestCaseInfo testCaseInfo : body) {
                    if (testCaseInfo != null) {
                        Long id = testCaseInfo.idAsLong();
                        if (id != null) {
                            externalTestCases.put(id, testCaseInfo);
                        }
                    }
                }
            } catch (RestClientException ex) {
                log.warn("Unable to load test cases for suite {} from ms_gestion: {}", suiteId, ex.getMessage());
            }
        }

        return externalTestCases;
    }

    private List<TestResultSummary> buildSummaries(
            List<CampaignTestCase> campaignTestCases,
            Map<Long, TestCase> localTestCases,
            Map<Long, TestSuite> suitesById,
            Map<Long, ExternalTestCaseInfo> externalTestCases,
            Map<Long, ExecutionResult> resultByTestCaseId) {

        List<TestResultSummary> summaries = new ArrayList<>();
        for (CampaignTestCase campaignTestCase : campaignTestCases) {
            Long testCaseId = campaignTestCase.getTestCaseId();
            TestCase localTestCase = localTestCases.get(testCaseId);
            ExecutionResult executionResult = resultByTestCaseId.get(testCaseId);
            ExternalTestCaseInfo remoteTestCase = externalTestCases.get(testCaseId);

            summaries.add(createSummary(campaignTestCase, localTestCase, suitesById, remoteTestCase, executionResult));
        }

        for (ExecutionResult executionResult : resultByTestCaseId.values()) {
            boolean alreadyIncluded = summaries.stream().anyMatch(summary -> summary.testCaseId().equals(executionResult.getTestCaseId()));
            if (!alreadyIncluded) {
                summaries.add(toFallbackSummary(executionResult, suitesById, externalTestCases));
            }
        }

        return summaries.stream()
                .sorted(Comparator.comparingInt(summary -> summary.executionOrder() == null ? Integer.MAX_VALUE : summary.executionOrder()))
                .collect(Collectors.toList());
    }

    private TestResultSummary createSummary(
            CampaignTestCase campaignTestCase,
            TestCase localTestCase,
            Map<Long, TestSuite> suitesById,
            ExternalTestCaseInfo remoteTestCase,
            ExecutionResult executionResult) {

        Long testCaseId = campaignTestCase.getTestCaseId();
        Long suiteId = localTestCase != null ? localTestCase.getSuiteId() : null;
        TestSuite suite = suiteId != null ? suitesById.get(suiteId) : null;
        String suiteName = suite != null && suite.getName() != null ? suite.getName() : (suiteId != null ? "Suite #" + suiteId : "Suite inconnue");
        String name = remoteTestCase != null && remoteTestCase.title != null ? remoteTestCase.title : "Test #" + testCaseId;
        String type = localTestCase != null && localTestCase.getType() != null
                ? localTestCase.getType().name()
                : remoteTestCase != null && remoteTestCase.type != null ? remoteTestCase.type : "UNKNOWN";
        Boolean flaky = remoteTestCase != null ? remoteTestCase.flaky : null;
        Boolean generated = localTestCase != null ? localTestCase.getGenerated() : null;
        String scriptPath = localTestCase != null ? localTestCase.getScriptPath() : null;

        return mapSummary(
                testCaseId,
                campaignTestCase.getExecutionOrder(),
                suiteName,
                suiteId,
                name,
                type,
                flaky,
                generated,
                scriptPath,
                executionResult
        );
    }

    private TestResultSummary toFallbackSummary(
            ExecutionResult executionResult,
            Map<Long, TestSuite> suitesById,
            Map<Long, ExternalTestCaseInfo> externalTestCases) {

        ExternalTestCaseInfo remoteTestCase = externalTestCases.get(executionResult.getTestCaseId());
        String type = executionResult.getTestType() != null ? executionResult.getTestType().name() : (remoteTestCase != null && remoteTestCase.type != null ? remoteTestCase.type : "UNKNOWN");
        Long suiteId = remoteTestCase != null ? remoteTestCase.suiteIdAsLong() : null;
        String suiteName = "Suite inconnue";
        if (suiteId != null && suitesById.containsKey(suiteId) && suitesById.get(suiteId).getName() != null) {
            suiteName = suitesById.get(suiteId).getName();
        } else if (suiteId != null) {
            suiteName = "Suite #" + suiteId;
        }

        return mapSummary(
                executionResult.getTestCaseId(),
                null,
                suiteName,
                suiteId,
                remoteTestCase != null && remoteTestCase.title != null ? remoteTestCase.title : "Test #" + executionResult.getTestCaseId(),
                type,
                remoteTestCase != null && remoteTestCase.flaky,
                null,
                null,
                executionResult
        );
    }

    private TestResultSummary mapSummary(
            Long testCaseId,
            Integer executionOrder,
            String suiteName,
            Long suiteId,
            String testName,
            String type,
            Boolean flaky,
            Boolean generated,
            String scriptPath,
            ExecutionResult executionResult) {

        String status = executionResult != null && executionResult.getStatus() != null
                ? executionResult.getStatus().name()
                : "NOT_EXECUTED";
        return new TestResultSummary(
                testCaseId,
                executionOrder,
                suiteId,
                suiteName,
                testName,
                type,
                status,
                executionResult != null ? executionResult.getDurationMs() : null,
                executionResult != null ? executionResult.getErrorMessage() : null,
                executionResult != null ? executionResult.getLogs() : null,
                executionResult != null ? executionResult.getAiAnalysis() : null,
                executionResult != null ? executionResult.getUxAnalysis() : null,
                executionResult != null ? executionResult.getScreenshotUrl() : null,
                Boolean.TRUE.equals(flaky),
                Boolean.TRUE.equals(generated),
                scriptPath,
                executionResult != null ? executionResult.getRetryCount() : null,
                executionResult != null ? executionResult.getRetryLog() : null
        );
    }

    private PreviousCampaignComparison resolvePreviousCampaignComparison(Campaign currentCampaign, double currentSuccessRate) {
        List<Campaign> campaigns = campaignRepository.findByProjectIdOrderByStartedAtDesc(currentCampaign.getProjectId());
        if (campaigns.size() < 2) {
            return new PreviousCampaignComparison("Premiere campagne", null, null, null);
        }

        for (int i = 0; i < campaigns.size(); i++) {
            if (!Objects.equals(campaigns.get(i).getId(), currentCampaign.getId())) {
                continue;
            }

            if (i + 1 >= campaigns.size()) {
                return new PreviousCampaignComparison("Premiere campagne", null, null, null);
            }

            Campaign previousCampaign = campaigns.get(i + 1);
            List<ExecutionResult> previousResults = executionResultRepository.findByCampaignId(previousCampaign.getId());
            double previousSuccessRate = computeSuccessRate(previousResults);
            double delta = currentSuccessRate - previousSuccessRate;
            String label = "Campagne precedente #" + previousCampaign.getId();
            String displayText;
            if (Math.abs(delta) < 0.01d) {
                displayText = "Performance stable";
            } else if (delta > 0) {
                displayText = String.format(Locale.US, "UP +%.1f points", delta);
            } else {
                displayText = String.format(Locale.US, "DOWN %.1f points", Math.abs(delta));
            }
            return new PreviousCampaignComparison(label, previousSuccessRate, delta, displayText);
        }

        return new PreviousCampaignComparison("Premiere campagne", null, null, null);
    }

    private double computeSuccessRate(List<ExecutionResult> results) {
        if (results == null || results.isEmpty()) {
            return 0.0d;
        }
        long successCount = results.stream().filter(result -> result.getStatus() == ExecutionResult.ResultStatus.SUCCESS).count();
        return successCount * 100.0d / results.size();
    }

    private Optional<ExternalCampaignInfo> fetchCampaignInfo(Long projectId, Long campaignId, String authorizationHeader) {
        try {
            // Use /api/internal/ — no SecurityUtils check, no user context needed
            ResponseEntity<ExternalCampaignInfo> response = restTemplate.exchange(
                    msGestionUrl + "/api/internal/projects/" + projectId + "/campaigns/" + campaignId,
                    HttpMethod.GET,
                    new HttpEntity<>(authorizationHeaders(authorizationHeader)),
                    ExternalCampaignInfo.class
            );
            return Optional.ofNullable(response.getBody());
        } catch (RestClientException ex) {
            log.warn("Unable to fetch campaign metadata from ms_gestion: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ExternalProjectInfo> fetchProjectInfo(Long projectId, String authorizationHeader) {
        try {
            ResponseEntity<ExternalProjectInfo> response = restTemplate.exchange(
                    msGestionUrl + "/api/internal/projects/" + projectId,
                    HttpMethod.GET,
                    new HttpEntity<>(authorizationHeaders(authorizationHeader)),
                    ExternalProjectInfo.class
            );
            return Optional.ofNullable(response.getBody());
        } catch (RestClientException ex) {
            log.warn("Unable to fetch project metadata from ms_gestion: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ExternalEnvironmentInfo> fetchEnvironmentInfo(Long projectId, Long environmentId, String authorizationHeader) {
        try {
            ResponseEntity<ExternalEnvironmentInfo> response = restTemplate.exchange(
                    msGestionUrl + "/api/internal/projects/" + projectId + "/environments/" + environmentId,
                    HttpMethod.GET,
                    new HttpEntity<>(authorizationHeaders(authorizationHeader)),
                    ExternalEnvironmentInfo.class
            );
            return Optional.ofNullable(response.getBody());
        } catch (RestClientException ex) {
            log.warn("Unable to fetch environment metadata from ms_gestion: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private HttpHeaders authorizationHeaders(String authorizationHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        return headers;
    }

    private void addCoverPage(PdfDocument pdfDocument, Document document, ReportData data, PdfFont boldFont, PdfFont regularFont) {
        PdfPage page = pdfDocument.addNewPage(PageSize.A4);
        Rectangle pageSize = page.getPageSize();
        PdfCanvas canvas = new PdfCanvas(page);
        canvas.saveState();
        canvas.setFillColor(NAVY);
        canvas.rectangle(pageSize.getLeft(), pageSize.getBottom(), pageSize.getWidth(), pageSize.getHeight());
        canvas.fill();
        canvas.restoreState();

        Canvas layout = new Canvas(canvas, pageSize);
        layout.add(new Paragraph(LOGO_TEXT)
                .setFont(boldFont)
                .setFontSize(28)
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(140));

        layout.add(new Paragraph("Rapport d'Execution de Campagne")
                .setFont(boldFont)
                .setFontSize(22)
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(12));

        layout.add(new Paragraph(safeValue(data.campaign().name()))
                .setFont(boldFont)
                .setFontSize(16)
                .setFontColor(GOLD)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(18));

        layout.add(new Paragraph("Projet: " + safeValue(data.project().name()))
                .setFont(regularFont)
                .setFontSize(12)
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(6));

        layout.add(new Paragraph("Version: " + safeValue(data.campaign().appVersion()))
                .setFont(regularFont)
                .setFontSize(12)
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(2));

        layout.add(new Paragraph("Environnement: " + safeValue(data.environment().name()))
                .setFont(regularFont)
                .setFontSize(12)
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(2));

        layout.add(new Paragraph("Debut: " + formatDateTime(data.campaign().startedAt) + "  |  Fin: " + formatDateTime(data.campaign().finishedAt))
                .setFont(regularFont)
                .setFontSize(11)
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(2));

        layout.add(new Paragraph("Duree: " + formatDuration(data.campaign().durationMs()))
                .setFont(regularFont)
                .setFontSize(11)
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(2));

        layout.add(new Paragraph("Rapport genere le " + formatDateTime(data.generatedAt()))
                .setFont(regularFont)
                .setFontSize(10)
                .setFontColor(new DeviceRgb(180, 180, 180))
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(2));

        Cell statusBadge = createStatusBadge(data, boldFont, true);
        statusBadge.setBorder(Border.NO_BORDER);
        Table badgeTable = new Table(1).setWidth(UnitValue.createPercentValue(40)).setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
        badgeTable.addCell(statusBadge);
        layout.add(badgeTable.setMarginTop(30));

        layout.close();
        document.add(new AreaBreak(AreaBreakType.LAST_PAGE));
    }

    private void addExecutiveSummary(Document document, PdfDocument pdfDocument, ReportData data, PdfFont regularFont, PdfFont boldFont) {
        document.add(new Paragraph("Resume executif").setFont(boldFont).setFontSize(18).setMarginBottom(8));

        // ── KPI table (5 columns) ──────────────────────────────────────────────
        Table metrics = new Table(new float[]{2, 2, 2, 2, 2});
        metrics.setWidth(UnitValue.createPercentValue(100));
        metrics.addHeaderCell(metricHeader("Total", boldFont));
        metrics.addHeaderCell(metricHeader("Succes", boldFont));
        metrics.addHeaderCell(metricHeader("Echecs", boldFont));
        metrics.addHeaderCell(metricHeader("Erreurs", boldFont));
        metrics.addHeaderCell(metricHeader("Duree", boldFont));
        metrics.addCell(metricValue(String.valueOf(data.totalTests()), boldFont, INFO));
        metrics.addCell(metricValue(String.valueOf(data.successCount()), boldFont, SUCCESS));
        metrics.addCell(metricValue(String.valueOf(data.failureCount()), boldFont, FAILURE));
        metrics.addCell(metricValue(String.valueOf(data.errorCount()), boldFont, WARNING));
        metrics.addCell(metricValue(formatDuration(data.campaign().durationMs()), boldFont, NAVY));
        document.add(metrics.setMarginBottom(8));

        // ── Success rate gauge (flow-based, no canvas coordinate issues) ────────
        double rate = Math.min(100.0, Math.max(0.0, data.successRate()));
        DeviceRgb gaugeColor = rate >= 80 ? SUCCESS : rate >= 50 ? WARNING : FAILURE;
        float filled = (float) Math.min(95.0, Math.max(5.0, rate));
        float empty  = 100f - filled;

        Table gaugeBar = new Table(new float[]{filled, empty});
        gaugeBar.setWidth(UnitValue.createPercentValue(100));
        Cell filledCell = new Cell().setHeight(22).setBackgroundColor(gaugeColor)
                .setBorder(Border.NO_BORDER)
                .add(new Paragraph(String.format(Locale.US, "%.0f%%", rate))
                        .setFont(boldFont).setFontSize(11).setFontColor(ColorConstants.WHITE)
                        .setTextAlignment(TextAlignment.CENTER));
        Cell emptyCell  = new Cell().setHeight(22).setBackgroundColor(new DeviceRgb(230, 230, 230))
                .setBorder(Border.NO_BORDER).add(new Paragraph(""));
        gaugeBar.addCell(rate > 2 ? filledCell : emptyCell.setBackgroundColor(FAILURE));
        if (rate > 2 && rate < 98) gaugeBar.addCell(emptyCell);
        document.add(new Paragraph("Taux de reussite").setFont(boldFont).setFontSize(10).setMarginBottom(2));
        document.add(gaugeBar.setMarginBottom(6));

        document.add(new Paragraph("Evolution vs campagne precedente: " + safeValue(data.previousComparisonText()))
                .setFont(regularFont).setFontSize(11).setMarginTop(10));
        document.add(new Paragraph(safeValue(data.previousCampaignLabel()))
                .setFont(regularFont).setFontSize(10).setFontColor(ColorConstants.GRAY));

        long aiCorrectedCount = data.allResults().stream()
                .filter(s -> s.retryCount() != null && s.retryCount() > 0).count();
        if (aiCorrectedCount > 0) {
            long aiFixedSuccess = data.allResults().stream()
                    .filter(s -> s.retryCount() != null && s.retryCount() > 0 && "SUCCESS".equals(s.status())).count();
            document.add(new Paragraph("Corrections IA: " + aiCorrectedCount + " test(s) corrige(s) automatiquement, "
                    + aiFixedSuccess + " avec succes")
                    .setFont(boldFont).setFontSize(11).setFontColor(WARNING).setMarginTop(6));
        }
    }

    private void addContextPage(Document document, ReportData data, PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph("Informations du contexte").setFont(boldFont).setFontSize(18).setMarginBottom(10));

        Table projectTable = new Table(new float[]{2, 4});
        projectTable.setWidth(UnitValue.createPercentValue(100));
        addContextRow(projectTable, "Projet", safeValue(data.project().name()), boldFont, regularFont);
        addContextRow(projectTable, "Description", safeValue(data.project().description()), boldFont, regularFont);
        document.add(projectTable);

        document.add(new Paragraph("Suites").setFont(boldFont).setFontSize(13).setMarginTop(12));
        Table suiteTable = new Table(new float[]{2.6f, 1.4f, 2.6f, 1.4f});
        suiteTable.setWidth(UnitValue.createPercentValue(100));
        suiteTable.addHeaderCell(contextHeader("Nom", boldFont));
        suiteTable.addHeaderCell(contextHeader("Type", boldFont));
        suiteTable.addHeaderCell(contextHeader("Depot Git", boldFont));
        suiteTable.addHeaderCell(contextHeader("Branche", boldFont));
        for (SuiteContext suite : data.suites()) {
            suiteTable.addCell(contextValue(suite.name(), regularFont));
            suiteTable.addCell(contextValue(suite.type(), regularFont));
            suiteTable.addCell(contextValue(safeValue(suite.gitRepoUrl()), regularFont));
            suiteTable.addCell(contextValue(safeValue(suite.gitBranch()), regularFont));
        }
        document.add(suiteTable);

        document.add(new Paragraph("Cas de test").setFont(boldFont).setFontSize(13).setMarginTop(12));
        Table testTable = new Table(new float[]{2.6f, 1.2f, 1.2f, 1.4f, 1.6f});
        testTable.setWidth(UnitValue.createPercentValue(100));
        testTable.addHeaderCell(contextHeader("Titre", boldFont));
        testTable.addHeaderCell(contextHeader("Type", boldFont));
        testTable.addHeaderCell(contextHeader("Priorite", boldFont));
        testTable.addHeaderCell(contextHeader("Risque", boldFont));
        testTable.addHeaderCell(contextHeader("Script", boldFont));
        for (TestCaseContext testCase : data.testCases()) {
            testTable.addCell(contextValue(testCase.title(), regularFont));
            testTable.addCell(contextValue(testCase.type(), regularFont));
            testTable.addCell(contextValue(safeValue(testCase.priority()), regularFont));
            testTable.addCell(contextValue(safeValue(testCase.riskLevel()), regularFont));
            String script = testCase.generated() ? "IA" : safeValue(testCase.scriptPath());
            testTable.addCell(contextValue(script, regularFont));
        }
        document.add(testTable);

        document.add(new Paragraph("Environnement").setFont(boldFont).setFontSize(13).setMarginTop(12));
        Table envTable = new Table(new float[]{2, 4});
        envTable.setWidth(UnitValue.createPercentValue(100));
        addContextRow(envTable, "URL Web", safeValue(data.localEnvironment() != null ? data.localEnvironment().getBaseUrlWeb() : null), boldFont, regularFont);
        addContextRow(envTable, "URL API", safeValue(data.localEnvironment() != null ? data.localEnvironment().getBaseUrlApi() : null), boldFont, regularFont);
        addContextRow(envTable, "Repository", safeValue(data.localEnvironment() != null ? data.localEnvironment().getGitRepoUrl() : null), boldFont, regularFont);
        addContextRow(envTable, "Branche", safeValue(data.localEnvironment() != null ? data.localEnvironment().getGitBranch() : null), boldFont, regularFont);
        addContextRow(envTable, "Base de donnees", safeValue(data.localEnvironment() != null ? data.localEnvironment().getDatabaseType() : null), boldFont, regularFont);
        document.add(envTable);
    }

    private void addDetailedResults(Document document, ReportData data, PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph("Resultats detailles").setFont(boldFont).setFontSize(18).setMarginBottom(10));
        if (data.allResults().isEmpty()) {
            document.add(messageBox("Aucun test execute pour cette campagne", boldFont));
            return;
        }

        // Suite-level summary table
        document.add(new Paragraph("Synthese par suite").setFont(boldFont).setFontSize(13).setMarginBottom(6));
        Table suiteSum = new Table(new float[]{3.0f, 1.2f, 1.2f, 1.2f, 1.4f});
        suiteSum.setWidth(UnitValue.createPercentValue(100));
        suiteSum.addHeaderCell(contextHeader("Suite", boldFont));
        suiteSum.addHeaderCell(contextHeader("Total", boldFont));
        suiteSum.addHeaderCell(contextHeader("Succes", boldFont));
        suiteSum.addHeaderCell(contextHeader("Echecs", boldFont));
        suiteSum.addHeaderCell(contextHeader("Taux", boldFont));
        for (Map.Entry<String, List<TestResultSummary>> entry : data.resultsBySuite().entrySet()) {
            int total = entry.getValue().size();
            int pass = (int) entry.getValue().stream().filter(s -> "SUCCESS".equals(s.status())).count();
            int fail = total - pass;
            double rate = total == 0 ? 0.0 : pass * 100.0 / total;
            suiteSum.addCell(contextValue(entry.getKey(), regularFont));
            suiteSum.addCell(contextValue(String.valueOf(total), regularFont));
            suiteSum.addCell(contextValue(String.valueOf(pass), regularFont));
            suiteSum.addCell(contextValue(String.valueOf(fail), regularFont));
            DeviceRgb rateColor = rate >= 80 ? SUCCESS : rate >= 50 ? WARNING : FAILURE;
            suiteSum.addCell(new Cell().setBorder(new SolidBorder(ColorConstants.WHITE, 1))
                    .add(new Paragraph(String.format(Locale.US, "%.0f%%", rate)).setFont(boldFont).setFontSize(9).setFontColor(rateColor)));
        }
        document.add(suiteSum.setMarginBottom(12));

        // Detailed per-test results grouped by suite
        for (Map.Entry<String, List<TestResultSummary>> suiteEntry : data.resultsBySuite().entrySet()) {
            document.add(new Paragraph(suiteEntry.getKey()).setFont(boldFont).setFontSize(14).setMarginTop(10).setMarginBottom(4)
                    .setBorderBottom(new SolidBorder(NAVY, 1)));

            for (TestResultSummary summary : suiteEntry.getValue()) {
                Table header = new Table(new float[]{4.0f, 1.0f, 1.0f});
                header.setWidth(UnitValue.createPercentValue(100));
                header.addCell(new Cell().setBorder(Border.NO_BORDER)
                        .add(new Paragraph(safeValue(summary.testName())).setFont(boldFont).setFontSize(12)));
                if (summary.retryCount() != null && summary.retryCount() > 0) {
                    header.addCell(new Cell().setBorder(Border.NO_BORDER)
                            .add(new Paragraph("IA x" + summary.retryCount()).setFont(regularFont).setFontSize(9).setFontColor(WARNING)));
                } else {
                    header.addCell(new Cell().setBorder(Border.NO_BORDER).add(new Paragraph("")));
                }
                header.addCell(statusBadgeCell(summary.status(), boldFont));
                document.add(header.setMarginTop(4));

                Table meta = new Table(new float[]{1.6f, 2.0f, 1.6f, 2.0f});
                meta.setWidth(UnitValue.createPercentValue(100));
                addContextRow(meta, "Type", safeValue(summary.type()), boldFont, regularFont);
                addContextRow(meta, "Duree", formatDuration(summary.durationMs()), boldFont, regularFont);
                addContextRow(meta, "Script", summary.generated() ? "IA" : safeValue(summary.scriptPath()), boldFont, regularFont);
                String errorType = llmAnalysisService.detectErrorType(summary.logs(), summary.errorMessage());
                String firstError = extractFirstError(summary.logs(), summary.errorMessage());
                String errorDisplay = "[" + errorType + "] " + firstError;
                addContextRow(meta, "Erreur", truncate(errorDisplay, ERROR_PREVIEW_CHARS), boldFont, regularFont);
                document.add(meta);

                if ("WEB".equalsIgnoreCase(summary.type()) && ("FAILURE".equals(summary.status()) || "ERROR".equals(summary.status()))) {
                    document.add(new Paragraph("Capture d'ecran").setFont(boldFont).setFontSize(11).setMarginTop(6));
                    if (summary.screenshotUrl() == null || summary.screenshotUrl().isBlank()) {
                        document.add(new Paragraph("Capture non disponible").setFont(regularFont).setFontSize(10));
                    } else {
                        try {
                            Path screenshotPath = Paths.get(summary.screenshotUrl());
                            if (Files.exists(screenshotPath) && Files.isRegularFile(screenshotPath)) {
                                byte[] bytes = Files.readAllBytes(screenshotPath);
                                Image image = new Image(ImageDataFactory.create(bytes));
                                image.setAutoScale(true);
                                document.add(image);
                            } else {
                                document.add(new Paragraph("Capture non disponible").setFont(regularFont).setFontSize(10));
                            }
                        } catch (Exception ex) {
                            document.add(new Paragraph("Capture non disponible").setFont(regularFont).setFontSize(10));
                        }
                    }
                }
            }
        }
    }

    private void addAiAnalysis(Document document, ReportData data, PdfFont boldFont, PdfFont monoFont) {
        document.add(new Paragraph("Analyse IA").setFont(boldFont).setFontSize(18).setMarginBottom(10));
        if (data.allResults().isEmpty()) {
            document.add(messageBox("Aucun test execute pour cette campagne", boldFont));
            return;
        }

        for (TestResultSummary summary : data.allResults()) {
            document.add(new Paragraph(safeValue(summary.testName())).setFont(boldFont).setFontSize(12).setMarginTop(6));
            Table box = new Table(new float[]{1.6f, 4.4f});
            box.setWidth(UnitValue.createPercentValue(100));
            box.setBackgroundColor(LIGHT_GRAY);
            box.addCell(contextHeader("Erreurs extraites", boldFont));
            box.addCell(new Cell().setBackgroundColor(LIGHT_GRAY).setBorder(new SolidBorder(ColorConstants.WHITE, 1))
                    .add(new Paragraph(llmAnalysisService.extractRelevantErrors(summary.logs()))
                            .setFont(monoFont).setFontSize(9)));
            box.addCell(contextHeader("Analyse IA", boldFont));
            String analysis = summary.aiAnalysis() != null && !summary.aiAnalysis().isBlank()
                    ? summary.aiAnalysis()
                    : "Analyse IA non disponible";
            box.addCell(new Cell().setBackgroundColor(LIGHT_GRAY).setBorder(new SolidBorder(ColorConstants.WHITE, 1))
                    .add(new Paragraph(analysis).setFont(monoFont).setFontSize(9)));
            document.add(box);
        }
    }

    private void addUxEvaluationSection(Document document, ReportData data, PdfFont boldFont, PdfFont monoFont) {
        document.add(new Paragraph("Evaluation UX").setFont(boldFont).setFontSize(18).setMarginBottom(10));

        List<TestResultSummary> uxResults = data.allResults().stream()
                .filter(this::isUxResult)
                .collect(Collectors.toList());

        if (uxResults.isEmpty()) {
            document.add(messageBox("Aucun test UX execute pour cette campagne", boldFont));
            return;
        }

        Table table = new Table(new float[]{2.6f, 1.2f, 1.2f, 3.0f});
        table.setWidth(UnitValue.createPercentValue(100));
        table.addHeaderCell(contextHeader("Test", boldFont));
        table.addHeaderCell(contextHeader("Plateforme", boldFont));
        table.addHeaderCell(contextHeader("Statut", boldFont));
        table.addHeaderCell(contextHeader("Analyse UX", boldFont));

        for (TestResultSummary summary : uxResults) {
            table.addCell(contextValue(safeValue(summary.testName()), monoFont));
            table.addCell(contextValue(resolveUxPlatform(summary.type()), monoFont));
            table.addCell(statusBadgeCell(summary.status(), boldFont));
            String analysis = summary.uxAnalysis() != null && !summary.uxAnalysis().isBlank()
                    ? summary.uxAnalysis()
                    : "Analyse UX non disponible";
            table.addCell(new Cell().setBorder(new SolidBorder(ColorConstants.WHITE, 1))
                    .add(new Paragraph(truncate(analysis, 500)).setFont(monoFont).setFontSize(9)));
        }

        document.add(table);

        for (TestResultSummary summary : uxResults) {
            if (summary.screenshotUrl() != null && !summary.screenshotUrl().isBlank()) {
                addUxScreenshot(document, summary.screenshotUrl(), boldFont);
            }
        }
    }

    private void addUxScreenshot(Document document, String screenshotUrl, PdfFont boldFont) {
        if (screenshotUrl == null || screenshotUrl.isBlank()) {
            return;
        }

        try {
            Path screenshotPath = Paths.get(screenshotUrl);
            if (!Files.exists(screenshotPath) || !Files.isRegularFile(screenshotPath)) {
                return;
            }

            byte[] bytes = Files.readAllBytes(screenshotPath);
            if (bytes.length == 0) {
                return;
            }

            document.add(new Paragraph("Capture d'écran de l'application évaluée")
                    .setFont(boldFont)
                    .setFontSize(11)
                    .setMarginTop(8)
                    .setMarginBottom(4));

            Image image = new Image(ImageDataFactory.create(bytes));
            image.scaleToFit(500f, 1000f);
            image.setAutoScale(false);
            document.add(image);
        } catch (Exception ex) {
            log.debug("Unable to add UX screenshot to PDF: {}", ex.getMessage());
        }
    }

    private void addMetricsSection(Document document, ReportData data, PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph("Metriques").setFont(boldFont).setFontSize(18).setMarginBottom(10));

        document.add(new Paragraph("Top 5 des tests les plus lents").setFont(boldFont).setFontSize(12).setMarginBottom(6));
        if (data.slowestTests().isEmpty()) {
            document.add(messageBox("Aucune donnee de duree disponible", boldFont));
        } else {
            Table slowTable = new Table(new float[]{3.4f, 2.0f, 1.4f});
            slowTable.setWidth(UnitValue.createPercentValue(100));
            slowTable.addHeaderCell(contextHeader("Test", boldFont));
            slowTable.addHeaderCell(contextHeader("Suite", boldFont));
            slowTable.addHeaderCell(contextHeader("Duree", boldFont));
            for (TestResultSummary summary : data.slowestTests()) {
                slowTable.addCell(contextValue(summary.testName(), regularFont));
                slowTable.addCell(contextValue(summary.suiteName(), regularFont));
                slowTable.addCell(contextValue(formatDuration(summary.durationMs()), regularFont));
            }
            document.add(slowTable);
        }

        document.add(new Paragraph("Temps d'execution par type").setFont(boldFont).setFontSize(12).setMarginTop(12));
        if (data.averageDurationByType().isEmpty()) {
            document.add(messageBox("Aucune donnee de performance par type", boldFont));
        } else {
            double max = data.averageDurationByType().values().stream().mapToDouble(Double::doubleValue).max().orElse(1d);
            Table typeTable = new Table(new float[]{1.6f, 4.4f});
            typeTable.setWidth(UnitValue.createPercentValue(100));
            typeTable.addHeaderCell(contextHeader("Type", boldFont));
            typeTable.addHeaderCell(contextHeader("Duree moyenne", boldFont));
            for (Map.Entry<String, Double> entry : data.averageDurationByType().entrySet()) {
                typeTable.addCell(contextValue(entry.getKey(), regularFont));
                float pct = (float) Math.min(95d, Math.max(5d, (entry.getValue() / max) * 100d));
                Table bar = new Table(new float[]{pct, 100f - pct});
                bar.setWidth(UnitValue.createPercentValue(100));
                bar.addCell(new Cell().setBorder(Border.NO_BORDER).setBackgroundColor(INFO)
                        .add(new Paragraph(formatDuration(entry.getValue().longValue())).setFont(regularFont).setFontSize(9).setFontColor(ColorConstants.WHITE)));
                bar.addCell(new Cell().setBorder(Border.NO_BORDER).add(new Paragraph(" ")));
                typeTable.addCell(new Cell().setBorder(new SolidBorder(ColorConstants.WHITE, 1)).add(bar));
            }
            document.add(typeTable);
        }
    }

    private void addFlakyTests(Document document, ReportData data, PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph("Tests instables").setFont(boldFont).setFontSize(18).setMarginBottom(10));
        if (data.flakyTests().isEmpty()) {
            document.add(messageBox("Aucun test instable detecte", boldFont));
            return;
        }

        Table table = new Table(new float[]{3.2f, 2.4f, 1.4f, 1.8f});
        table.setWidth(UnitValue.createPercentValue(100));
        table.addHeaderCell(contextHeader("Test", boldFont));
        table.addHeaderCell(contextHeader("Suite", boldFont));
        table.addHeaderCell(contextHeader("Type", boldFont));
        table.addHeaderCell(contextHeader("Statut", boldFont));
        for (TestResultSummary summary : data.flakyTests()) {
            table.addCell(contextValue(summary.testName(), regularFont));
            table.addCell(contextValue(summary.suiteName(), regularFont));
            table.addCell(contextValue(safeValue(summary.type()), regularFont));
            table.addCell(statusBadgeCell(summary.status(), boldFont));
        }
        document.add(table);
    }

    private void addRecommendations(Document document, ReportData data, PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph("Recommandations").setFont(boldFont).setFontSize(18).setMarginBottom(10));
        document.add(new Paragraph(data.recommendations()).setFont(regularFont).setFontSize(10));
    }

    private void addAppendix(Document document, ReportData data, PdfFont boldFont, PdfFont monoFont) {
        document.add(new Paragraph("Annexe - Scripts generes par IA").setFont(boldFont).setFontSize(18).setMarginBottom(10));
        for (GeneratedScript script : data.generatedScripts()) {
            document.add(new Paragraph(script.title()).setFont(boldFont).setFontSize(12).setMarginTop(6));
            Paragraph code = new Paragraph(script.code())
                    .setFont(monoFont)
                    .setFontSize(8)
                    .setBackgroundColor(LIGHT_GRAY)
                    .setMarginBottom(6);
            document.add(code);
        }
    }

    private void addAiCorrectionsSection(Document document, ReportData data, PdfFont boldFont, PdfFont monoFont, PdfFont regularFont) {
        document.add(new Paragraph("Corrections automatiques par IA").setFont(boldFont).setFontSize(18).setMarginBottom(10));
        document.add(new Paragraph("L'IA a detecte des erreurs dans certains scripts generes et les a corriges automatiquement.")
                .setFont(regularFont).setFontSize(10).setMarginBottom(8));

        for (TestResultSummary summary : data.allResults()) {
            if (summary.retryCount() == null || summary.retryCount() == 0) continue;
            if (summary.retryLog() == null || summary.retryLog().isBlank()) continue;

            Table header = new Table(new float[]{4.0f, 1.4f, 1.6f});
            header.setWidth(UnitValue.createPercentValue(100));
            header.addCell(new Cell().setBorder(Border.NO_BORDER)
                    .add(new Paragraph(safeValue(summary.testName())).setFont(boldFont).setFontSize(12)));
            header.addCell(new Cell().setBorder(Border.NO_BORDER)
                    .add(new Paragraph(summary.retryCount() + " correction(s)").setFont(regularFont).setFontSize(10).setFontColor(WARNING)));
            header.addCell(statusBadgeCell(summary.status(), boldFont));
            document.add(header.setMarginTop(8));

            Paragraph retryContent = new Paragraph(summary.retryLog())
                    .setFont(monoFont)
                    .setFontSize(8)
                    .setBackgroundColor(LIGHT_GRAY)
                    .setMarginBottom(6);
            document.add(retryContent);
        }
    }

    private boolean hasAiCorrections(ReportData data) {
        return data.allResults().stream()
                .anyMatch(s -> s.retryCount() != null && s.retryCount() > 0
                        && s.retryLog() != null && !s.retryLog().isBlank());
    }

    private boolean hasUxTests(ReportData data) {
        return data.allResults().stream().anyMatch(this::isUxResult);
    }

    /**
     * Extracts the first meaningful [ERROR] line from Maven logs.
     * Falls back to the errorMessage if no [ERROR] line is found.
     */
    private String extractFirstError(String logs, String errorMessage) {
        if (logs != null) {
            for (String line : logs.split("\\r?\\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("[ERROR]") && !trimmed.contains("[Help")
                        && !trimmed.contains("-> [Help") && trimmed.length() > 10) {
                    return trimmed.replaceFirst("^\\[ERROR\\]\\s*", "");
                }
            }
        }
        return errorMessage != null && !errorMessage.isBlank() ? errorMessage : "—";
    }

    private void addContextRow(Table table, String label, String value, PdfFont boldFont, PdfFont regularFont) {
        table.addCell(contextHeader(label, boldFont));
        table.addCell(contextValue(value, regularFont));
    }

    private Cell contextHeader(String text, PdfFont font) {
        return new Cell().setBackgroundColor(LIGHT_GRAY).setBorder(new SolidBorder(ColorConstants.WHITE, 1))
                .add(new Paragraph(text).setFont(font).setFontSize(9));
    }

    private Cell contextValue(String text, PdfFont font) {
        return new Cell().setBorder(new SolidBorder(ColorConstants.WHITE, 1))
                .add(new Paragraph(safeValue(text)).setFont(font).setFontSize(9));
    }

    private Cell messageBox(String message, PdfFont font) {
        return new Cell().setBackgroundColor(LIGHT_GRAY).setBorder(new SolidBorder(ColorConstants.WHITE, 1))
                .add(new Paragraph(message).setFont(font).setFontSize(11).setTextAlignment(TextAlignment.CENTER));
    }

    private Cell statusBadgeCell(String status, PdfFont boldFont) {
        return createStatusBadge(status, boldFont, false);
    }

    private Cell createStatusBadge(ReportData data, PdfFont boldFont, boolean large) {
        return createStatusBadge(resolveGlobalStatus(data), boldFont, large);
    }

    private Cell createStatusBadge(String status, PdfFont boldFont, boolean large) {
        DeviceRgb color = statusColor(status);
        int size = large ? 12 : 9;
        return new Cell().setBackgroundColor(color).setBorder(Border.NO_BORDER)
                .add(new Paragraph(status)
                        .setFont(boldFont)
                        .setFontSize(size)
                        .setFontColor(ColorConstants.WHITE)
                        .setTextAlignment(TextAlignment.CENTER));
    }

    private String resolveGlobalStatus(ReportData data) {
        if (data.totalTests() == 0) {
            return "PARTIEL";
        }
        if (data.failureCount() > 0 || data.errorCount() > 0) {
            return data.successCount() > 0 ? "PARTIEL" : "ECHEC";
        }
        return "REUSSI";
    }

    private DeviceRgb statusColor(String status) {
        if (status == null) {
            return INFO;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "SUCCESS", "REUSSI" -> SUCCESS;
            case "FAILURE", "ERROR", "ECHEC" -> FAILURE;
            case "PARTIEL" -> WARNING;
            default -> INFO;
        };
    }

    private Cell metricHeader(String text, PdfFont font) {
        return new Cell().setBackgroundColor(LIGHT_GRAY).setBorder(new SolidBorder(ColorConstants.WHITE, 1))
                .add(new Paragraph(text).setFont(font).setFontSize(9).setTextAlignment(TextAlignment.CENTER));
    }

    private Cell metricValue(String text, PdfFont font, DeviceRgb color) {
        return new Cell().setBackgroundColor(SOFT_GRAY).setBorder(new SolidBorder(ColorConstants.WHITE, 1))
                .add(new Paragraph(text).setFont(font).setFontSize(14).setFontColor(color).setTextAlignment(TextAlignment.CENTER));
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "N/A";
        }
        return dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    private String formatDuration(Long durationMs) {
        if (durationMs == null) {
            return "N/A";
        }
        if (durationMs < 1000) {
            return durationMs + " ms";
        }
        return String.format(Locale.US, "%.1f s", durationMs / 1000.0d);
    }

    private String truncate(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "N/A";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "...";
    }

    private String extractTail(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "N/A";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(text.length() - maxChars);
    }

    private boolean isUxResult(TestResultSummary summary) {
        if (summary == null || summary.type() == null) {
            return false;
        }
        return "FUNCTIONAL_WEB".equalsIgnoreCase(summary.type()) || "FUNCTIONAL_MOBILE".equalsIgnoreCase(summary.type());
    }

    private String resolveUxPlatform(String type) {
        if (type == null) {
            return "—";
        }
        if ("FUNCTIONAL_WEB".equalsIgnoreCase(type)) {
            return "Web";
        }
        if ("FUNCTIONAL_MOBILE".equalsIgnoreCase(type)) {
            return "Mobile";
        }
        return type;
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private static class PageDecorator implements IEventHandler {
        private final LocalDateTime generatedAt;
        private final PdfFont regularFont;
        private final PdfFont boldFont;

        private PageDecorator(LocalDateTime generatedAt, PdfFont regularFont, PdfFont boldFont) {
            this.generatedAt = generatedAt;
            this.regularFont = regularFont;
            this.boldFont = boldFont;
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent pdfEvent = (PdfDocumentEvent) event;
            PdfDocument pdfDocument = pdfEvent.getDocument();
            PdfPage page = pdfEvent.getPage();
            Rectangle pageSize = page.getPageSize();

            PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdfDocument);
            Canvas canvas = new Canvas(pdfCanvas, pageSize);
            canvas.showTextAligned(new Paragraph(LOGO_TEXT).setFont(boldFont).setFontSize(9),
                    pageSize.getLeft() + 36, pageSize.getTop() - 18, TextAlignment.LEFT);
            canvas.showTextAligned(new Paragraph("Rapport de campagne").setFont(regularFont).setFontSize(9),
                    pageSize.getLeft() + 36, pageSize.getTop() - 30, TextAlignment.LEFT);
            canvas.showTextAligned(new Paragraph("Genere le " + generatedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                            .setFont(regularFont).setFontSize(8),
                    pageSize.getRight() - 36, pageSize.getBottom() + 20, TextAlignment.RIGHT);
            canvas.showTextAligned(new Paragraph(String.valueOf(pdfDocument.getPageNumber(page))).setFont(regularFont).setFontSize(8),
                    pageSize.getRight() - 36, pageSize.getBottom() + 10, TextAlignment.RIGHT);
        }
    }

    private record PreviousCampaignComparison(String label, Double previousSuccessRate, Double delta, String displayText) {}

    private record ReportData(
            ExternalCampaignInfo campaign,
            ExternalProjectInfo project,
            ExternalEnvironmentInfo environment,
            Environment localEnvironment,
            List<TestResultSummary> allResults,
            Map<String, List<TestResultSummary>> resultsBySuite,
            List<TestResultSummary> flakyTests,
            List<TestResultSummary> slowestTests,
            Map<String, Double> averageDurationByType,
            int totalTests,
            int successCount,
            int failureCount,
            int errorCount,
            double successRate,
            String previousCampaignLabel,
            Double previousSuccessRate,
            Double successRateDelta,
            String previousComparisonText,
            List<SuiteContext> suites,
            List<TestCaseContext> testCases,
            List<GeneratedScript> generatedScripts,
            String recommendations,
            LocalDateTime generatedAt
    ) {}

    private record TestResultSummary(
            Long testCaseId,
            Integer executionOrder,
            Long suiteId,
            String suiteName,
            String testName,
            String type,
            String status,
            Long durationMs,
            String errorMessage,
            String logs,
            String aiAnalysis,
            String uxAnalysis,
            String screenshotUrl,
            boolean flaky,
            boolean generated,
            String scriptPath,
            Integer retryCount,
            String retryLog
    ) {}

    private record SuiteContext(String name, String type, String gitRepoUrl, String gitBranch) {}

    private record TestCaseContext(Long id, String title, String type, String priority, String riskLevel, boolean generated, String scriptPath) {}

    private record GeneratedScript(Long id, String title, String code) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ExternalCampaignInfo {
        public String id;
        public String projectId;
        public String environmentId;
        public String name;
        public String appVersion;
        public String gitBranch;
        public String triggerMode;
        public String status;
        public LocalDateTime startedAt;
        public LocalDateTime finishedAt;
        public LocalDateTime createdAt;

        public ExternalCampaignInfo() {}

        private static ExternalCampaignInfo fallback(Campaign campaign) {
            ExternalCampaignInfo info = new ExternalCampaignInfo();
            info.id = String.valueOf(campaign.getId());
            info.projectId = campaign.getProjectId() == null ? null : String.valueOf(campaign.getProjectId());
            info.environmentId = campaign.getEnvironmentId() == null ? null : String.valueOf(campaign.getEnvironmentId());
            info.name = "Campagne #" + campaign.getId();
            info.appVersion = "N/A";
            info.gitBranch = campaign.getGitBranch();
            info.triggerMode = campaign.getTriggerMode() != null ? campaign.getTriggerMode().name() : null;
            info.status = campaign.getStatus() != null ? campaign.getStatus().name() : null;
            info.startedAt = campaign.getStartedAt();
            info.finishedAt = campaign.getFinishedAt();
            return info;
        }

        private String name() {
            return name == null || name.isBlank() ? "Campagne #" + id : name;
        }

        private String appVersion() {
            return appVersion;
        }

        private Long durationMs() {
            if (startedAt == null || finishedAt == null) {
                return null;
            }
            return Duration.between(startedAt, finishedAt).toMillis();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ExternalProjectInfo {
        public String id;
        public String name;
        public String description;

        public ExternalProjectInfo() {}

        private static ExternalProjectInfo fallback(Long projectId) {
            ExternalProjectInfo info = new ExternalProjectInfo();
            info.id = String.valueOf(projectId);
            info.name = "Projet #" + projectId;
            return info;
        }

        private String name() {
            return name == null || name.isBlank() ? "Projet #" + id : name;
        }

        private String description() {
            return description == null || description.isBlank() ? "N/A" : description;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ExternalEnvironmentInfo {
        public String id;
        public String name;

        public ExternalEnvironmentInfo() {}

        private static ExternalEnvironmentInfo fallback(Long environmentId) {
            ExternalEnvironmentInfo info = new ExternalEnvironmentInfo();
            info.id = String.valueOf(environmentId);
            info.name = "Environnement #" + environmentId;
            return info;
        }

        private String name() {
            return name == null || name.isBlank() ? "Environnement #" + id : name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ExternalTestCaseInfo {
        public String id;
        public String suiteId;
        public String title;
        public String type;
        public Boolean flaky;
        public String priority;
        public String riskLevel;

        public ExternalTestCaseInfo() {}

        private Long idAsLong() {
            if (id == null) return null;
            try {
                return Long.parseLong(id);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private Long suiteIdAsLong() {
            if (suiteId == null) return null;
            try {
                return Long.parseLong(suiteId);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
