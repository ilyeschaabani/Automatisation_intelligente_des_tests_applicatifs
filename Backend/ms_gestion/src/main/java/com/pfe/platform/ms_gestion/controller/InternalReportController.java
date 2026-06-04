package com.pfe.platform.ms_gestion.controller;

import com.pfe.platform.ms_gestion.dto.response.CampaignResponse;
import com.pfe.platform.ms_gestion.dto.response.EnvironmentResponse;
import com.pfe.platform.ms_gestion.dto.response.ProjectResponse;
import com.pfe.platform.ms_gestion.dto.response.TestCaseResponse;
import com.pfe.platform.ms_gestion.entity.Campaign;
import com.pfe.platform.ms_gestion.entity.Environment;
import com.pfe.platform.ms_gestion.entity.Project;
import com.pfe.platform.ms_gestion.entity.TestCase;
import com.pfe.platform.ms_gestion.repository.CampaignRepository;
import com.pfe.platform.ms_gestion.repository.CampaignTestCaseRepository;
import com.pfe.platform.ms_gestion.repository.EnvironmentRepository;
import com.pfe.platform.ms_gestion.repository.ProjectRepository;
import com.pfe.platform.ms_gestion.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Internal endpoints for service-to-service calls (ms-execution → ms_gestion).
 * No SecurityUtils / membership checks — read-only, no user context required.
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalReportController {

    private final ProjectRepository projectRepository;
    private final CampaignRepository campaignRepository;
    private final EnvironmentRepository environmentRepository;
    private final TestCaseRepository testCaseRepository;
    private final CampaignTestCaseRepository campaignTestCaseRepository;

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable Long projectId) {
        return projectRepository.findById(projectId)
                .map(p -> ResponseEntity.ok(ProjectResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .description(p.getDescription())
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/projects/{projectId}/campaigns/{campaignId}")
    public ResponseEntity<CampaignResponse> getCampaign(
            @PathVariable Long projectId,
            @PathVariable Long campaignId) {
        return campaignRepository.findById(campaignId)
                .map(c -> {
                    Long envId = c.getEnvironment() != null ? c.getEnvironment().getId() : null;
                    Long projId = c.getProject() != null ? c.getProject().getId() : null;
                    return ResponseEntity.ok(CampaignResponse.builder()
                            .id(c.getId())
                            .projectId(projId)
                            .environmentId(envId)
                            .name(c.getName())
                            .appVersion(c.getAppVersion())
                            .gitBranch(c.getGitBranch())
                            .triggerMode(c.getTriggerMode() != null ? c.getTriggerMode().name() : null)
                            .status(c.getStatus() != null ? c.getStatus().name() : null)
                            .startedAt(c.getStartedAt())
                            .finishedAt(c.getFinishedAt())
                            .createdAt(c.getCreatedAt())
                            .build());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/projects/{projectId}/environments/{environmentId}")
    public ResponseEntity<EnvironmentResponse> getEnvironment(
            @PathVariable Long projectId,
            @PathVariable Long environmentId) {
        return environmentRepository.findById(environmentId)
                .map(e -> ResponseEntity.ok(EnvironmentResponse.builder()
                        .id(e.getId())
                        .name(e.getName())
                        .baseUrlWeb(e.getBaseUrlWeb())
                        .baseUrlApi(e.getBaseUrlApi())
                        .gitRepoUrl(e.getGitRepoUrl())
                        .gitBranch(e.getGitBranch())
                        .databaseType(e.getDatabaseType())
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/suites/{suiteId}/testcases")
    public ResponseEntity<List<TestCaseResponse>> getTestCasesBySuite(@PathVariable Long suiteId) {
        List<TestCase> cases = testCaseRepository.findBySuiteId(suiteId);
        List<TestCaseResponse> responses = cases.stream()
                .map(tc -> TestCaseResponse.builder()
                        .id(tc.getId())
                        .suiteId(suiteId)
                        .title(tc.getTitle())
                        .description(tc.getDescription())
                        .type(tc.getType() != null ? tc.getType().name() : null)
                        .priority(tc.getPriority())
                        .riskLevel(tc.getRiskLevel() != null ? tc.getRiskLevel().name() : null)
                        .scriptPath(tc.getScriptPath())
                        .tags(tc.getTags())
                        .active(tc.getActive())
                        .flaky(tc.getFlaky())
                        .generated(tc.getGenerated())
                        .generatedCode(tc.getGeneratedCode())
                        .databaseType(tc.getDatabaseType())
                        .targetClassName(tc.getTargetClassName())
                        .build())
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/testcases")
    public ResponseEntity<List<TestCaseResponse>> getTestCases(@RequestParam List<Long> ids) {
        List<TestCase> cases = testCaseRepository.findAllById(ids);
        List<TestCaseResponse> responses = cases.stream()
                .map(tc -> TestCaseResponse.builder()
                        .id(tc.getId())
                        .suiteId(tc.getSuite() != null ? tc.getSuite().getId() : null)
                        .title(tc.getTitle())
                        .description(tc.getDescription())
                        .type(tc.getType() != null ? tc.getType().name() : null)
                        .priority(tc.getPriority())
                        .riskLevel(tc.getRiskLevel() != null ? tc.getRiskLevel().name() : null)
                        .scriptPath(tc.getScriptPath())
                        .tags(tc.getTags())
                        .active(tc.getActive())
                        .flaky(tc.getFlaky())
                        .generated(tc.getGenerated())
                        .generatedCode(tc.getGeneratedCode())
                        .databaseType(tc.getDatabaseType())
                        .targetClassName(tc.getTargetClassName())
                        .build())
                .toList();
        return ResponseEntity.ok(responses);
    }

}
