package com.pfe.platform.ms_gestion.service;


import com.pfe.platform.ms_gestion.dto.request.CreateCampaignRequest;
import com.pfe.platform.ms_gestion.dto.response.CampaignResponse;
import com.pfe.platform.ms_gestion.dto.response.TestCaseWithStatusResponse;
import com.pfe.platform.ms_gestion.entity.*;
import com.pfe.platform.ms_gestion.repository.*;
import com.pfe.platform.ms_gestion.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {
    private final CampaignRepository campaignRepository;
    private final CampaignTestCaseRepository campaignTestCaseRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final TestCaseRepository testCaseRepository;
    private final ProjectAccessService projectAccessService;
    private final RestTemplate restTemplate;

    @Value("${ms-execution.service.url:http://localhost:8083}")
    private String msExecutionUrl;

    @Transactional
    public CampaignResponse create(Long projectId, CreateCampaignRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Project project = getProjectOrThrow(projectId);
        projectAccessService.checkMembership(project);

        Environment env = environmentRepository.findById(request.getEnvironmentId())
                .orElseThrow(() -> new RuntimeException("Environnement non trouvé"));
        if (!env.getProject().getId().equals(projectId)) {
            throw new RuntimeException("L'environnement n'appartient pas à ce projet");
        }

        Campaign campaign = new Campaign();
        campaign.setProject(project);
        campaign.setEnvironment(env);
        campaign.setName(request.getName());
        campaign.setAppVersion(request.getAppVersion());
        campaign.setGitBranch(request.getGitBranch() != null ? request.getGitBranch() : project.getGitDefaultBranch());
        campaign.setTriggerMode(Campaign.TriggerMode.valueOf(request.getTriggerMode().toUpperCase()));
        campaign = campaignRepository.save(campaign);

        // Ajouter les cas sélectionnés
        if (request.getTestCaseIds() != null && !request.getTestCaseIds().isEmpty()) {
            int order = 1;
            for (Long testCaseId : request.getTestCaseIds()) {
                TestCase tc = testCaseRepository.findById(testCaseId)
                        .orElseThrow(() -> new RuntimeException("Cas de test " + testCaseId + " introuvable"));
                // Vérifier que le cas appartient bien à une suite du projet
                if (!tc.getSuite().getProject().getId().equals(projectId)) {
                    throw new RuntimeException("Le cas de test " + testCaseId + " n'appartient pas au projet");
                }

                CampaignTestCase ctc = new CampaignTestCase();
                ctc.setCampaign(campaign);
                ctc.setTestCase(tc);
                ctc.setExecutionOrder(order++);
                campaignTestCaseRepository.save(ctc);
            }
        }

        return mapToResponse(campaign);
    }

    public List<CampaignResponse> listForProject(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        projectAccessService.checkMembership(project);
        return campaignRepository.findByProjectId(projectId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<CampaignResponse> listAll() {
        List<Long> projectIds = projectAccessService.getAccessibleProjectIds();
        if (projectIds.isEmpty()) return List.of();
        return campaignRepository.findByProjectIdIn(projectIds)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public CampaignResponse getCampaign(Long projectId, Long campaignId) {
        Campaign campaign = getCampaignOrThrow(campaignId, projectId);
        projectAccessService.checkMembership(campaign.getProject());
        return mapToResponse(campaign);
    }

    public List<TestCaseWithStatusResponse> getTestCasesForCampaign(Long projectId, Long campaignId) {
        Campaign campaign = getCampaignOrThrow(campaignId, projectId);
        projectAccessService.checkMembership(campaign.getProject());

        // Fetch test cases linked to this campaign
        List<CampaignTestCase> campaignTestCases = campaignTestCaseRepository.findByCampaignId(campaignId);
        
        // Fetch execution results from ms-execution
        Map<Long, Map<String, Object>> executionStatusMap = new java.util.HashMap<>();
        try {
            String url = msExecutionUrl + "/api/execution/results/" + campaignId;
            List<Map<String, Object>> results = restTemplate.getForObject(url, List.class);
            if (results != null) {
                for (Map<String, Object> result : results) {
                    Long testCaseId = ((Number) result.get("testCaseId")).longValue();
                    executionStatusMap.put(testCaseId, result);
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch execution results from ms-execution for campaign {}: {}", campaignId, e.getMessage());
        }

        // Map to response DTOs
        return campaignTestCases.stream()
                .map(ctc -> mapTestCaseToResponse(ctc.getTestCase(), executionStatusMap.get(ctc.getTestCase().getId())))
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .collect(Collectors.toList());
    }

    private TestCaseWithStatusResponse mapTestCaseToResponse(TestCase tc, Map<String, Object> executionResult) {
        String executionStatus = null;
        Long executionDurationMs = null;
        String lastErrorMessage = null;

        if (executionResult != null) {
            Object status = executionResult.get("status");
            if (status != null) {
                // Map backend status (SUCCESS, FAILURE, ERROR) to frontend status
                String backendStatus = status.toString();
                executionStatus = backendStatus.equals("SUCCESS") ? "FINISHED" : "ERROR";
            }
            executionDurationMs = executionResult.get("durationMs") != null 
                ? ((Number) executionResult.get("durationMs")).longValue() 
                : null;
            lastErrorMessage = (String) executionResult.get("errorMessage");
        }

        return TestCaseWithStatusResponse.builder()
                .id(tc.getId())
                .title(tc.getTitle())
                .description(tc.getDescription())
                .type(tc.getType() != null ? tc.getType().name() : null)
                .priority(tc.getPriority())
                .riskLevel(tc.getRiskLevel() != null ? tc.getRiskLevel().name() : null)
                .scriptPath(tc.getScriptPath())
                .tags(tc.getTags())
                .maxDurationSeconds(tc.getMaxDurationSeconds())
                .active(tc.getActive())
                .flaky(tc.getFlaky())
                .createdAt(tc.getCreatedAt())
                .executionStatus(executionStatus)
                .executionDurationMs(executionDurationMs)
                .lastErrorMessage(lastErrorMessage)
                .build();
    }

    // Méthodes privées (réutiliser les mêmes que dans les autres services,
    // ou créer une classe utilitaire pour éviter la duplication)
    private Project getProjectOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Projet non trouvé"));
    }

    private Campaign getCampaignOrThrow(Long campaignId, Long projectId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campagne non trouvée"));
        if (!campaign.getProject().getId().equals(projectId)) {
            throw new RuntimeException("La campagne n'appartient pas à ce projet");
        }
        return campaign;
    }

    private CampaignResponse mapToResponse(Campaign c) {
        return CampaignResponse.builder()
                .id(c.getId())
                .projectId(c.getProject().getId())
                .environmentId(c.getEnvironment().getId())
                .name(c.getName())
                .appVersion(c.getAppVersion())
                .gitBranch(c.getGitBranch())
                .triggerMode(c.getTriggerMode().name())
                .status(c.getStatus().name())
                .startedAt(c.getStartedAt())
                .finishedAt(c.getFinishedAt())
                .createdAt(c.getCreatedAt())
                .build();
    }


    /**
     * Returns test cases belonging to the project that are NOT yet in the campaign.
     * Used to display available tests that can be added to an existing campaign.
     */
    public List<TestCaseWithStatusResponse> getAvailableTestCases(Long projectId, Long campaignId) {
        Campaign campaign = getCampaignOrThrow(campaignId, projectId);
        projectAccessService.checkMembership(campaign.getProject());

        // IDs already in the campaign
        List<Long> alreadyIn = campaignTestCaseRepository.findByCampaignId(campaignId)
                .stream().map(ctc -> ctc.getTestCase().getId()).toList();

        // All test cases in the project (through suites)
        List<TestCase> allProjectTestCases = testCaseRepository.findBySuiteProjectId(projectId);

        return allProjectTestCases.stream()
                .filter(tc -> !alreadyIn.contains(tc.getId()))
                .map(tc -> mapTestCaseToResponse(tc, null))
                .toList();
    }

    /**
     * Adds one or more test cases to an existing campaign.
     * Blocked if the campaign is currently RUNNING.
     */
    @Transactional
    public void addTestCasesToCampaign(Long projectId, Long campaignId, List<Long> testCaseIds) {
        Campaign campaign = getCampaignOrThrow(campaignId, projectId);
        projectAccessService.checkMembership(campaign.getProject());

        if (campaign.getStatus() == Campaign.CampaignStatus.RUNNING) {
            throw new RuntimeException("Impossible d'ajouter des tests à une campagne en cours d'exécution.");
        }

        // Start execution order after current max
        int nextOrder = 1;
        Integer currentMax = campaignTestCaseRepository.findMaxExecutionOrderByCampaignId(campaignId);
        if (currentMax != null) nextOrder = currentMax + 1;

        for (Long testCaseId : testCaseIds) {
            // Skip duplicates
            if (campaignTestCaseRepository.existsByCampaignIdAndTestCaseId(campaignId, testCaseId)) continue;

            TestCase tc = testCaseRepository.findById(testCaseId)
                    .orElseThrow(() -> new RuntimeException("Cas de test introuvable : " + testCaseId));
            if (!tc.getSuite().getProject().getId().equals(projectId)) {
                throw new RuntimeException("Le cas de test " + testCaseId + " n'appartient pas au projet");
            }

            CampaignTestCase ctc = new CampaignTestCase();
            ctc.setCampaign(campaign);
            ctc.setTestCase(tc);
            ctc.setExecutionOrder(nextOrder++);
            campaignTestCaseRepository.save(ctc);
        }
    }

    /**
     * Removes a test case from an existing campaign.
     * Blocked if the campaign is currently RUNNING.
     */
    @Transactional
    public void removeTestCaseFromCampaign(Long projectId, Long campaignId, Long testCaseId) {
        Campaign campaign = getCampaignOrThrow(campaignId, projectId);
        projectAccessService.checkMembership(campaign.getProject());

        if (campaign.getStatus() == Campaign.CampaignStatus.RUNNING) {
            throw new RuntimeException("Impossible de retirer un test d'une campagne en cours d'exécution.");
        }
        campaignTestCaseRepository.deleteByCampaignIdAndTestCaseId(campaignId, testCaseId);
    }

    @Transactional
    public void delete(Long projectId, Long campaignId) {
        Campaign campaign = getCampaignOrThrow(campaignId, projectId);
        projectAccessService.checkMembership(campaign.getProject());
        
        // Delete associated campaign test cases
        campaignTestCaseRepository.deleteByCampaignId(campaignId);
        
        // Delete the campaign
        campaignRepository.deleteById(campaignId);
    }
}
