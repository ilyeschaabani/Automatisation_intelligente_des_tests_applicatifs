package com.pfe.platform.ms_gestion.service;


import com.pfe.platform.ms_gestion.dto.request.CreateCampaignRequest;
import com.pfe.platform.ms_gestion.dto.response.CampaignResponse;
import com.pfe.platform.ms_gestion.entity.*;
import com.pfe.platform.ms_gestion.repository.*;
import com.pfe.platform.ms_gestion.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CampaignService {
    private final CampaignRepository campaignRepository;
    private final CampaignTestCaseRepository campaignTestCaseRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final TestCaseRepository testCaseRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional
    public CampaignResponse create(Long projectId, CreateCampaignRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Project project = getProjectOrThrow(projectId);
        checkProjectRole(project, userId, ProjectMember.Role.ADMIN, ProjectMember.Role.TESTER);

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
        checkMembership(project, SecurityUtils.getCurrentUserId());
        return campaignRepository.findByProjectId(projectId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public CampaignResponse getCampaign(Long projectId, Long campaignId) {
        Campaign campaign = getCampaignOrThrow(campaignId, projectId);
        checkMembership(campaign.getProject(), SecurityUtils.getCurrentUserId());
        return mapToResponse(campaign);
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

    private void checkMembership(Project project, Long userId) {
        if (!projectMemberRepository.existsByProjectIdAndUserId(project.getId(), userId)) {
            throw new RuntimeException("Vous n'êtes pas membre de ce projet");
        }
    }

    private void checkProjectRole(Project project, Long userId, ProjectMember.Role... allowedRoles) {
        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserId(project.getId(), userId)
                .orElseThrow(() -> new RuntimeException("Vous n'êtes pas membre de ce projet"));
        boolean authorized = false;
        for (ProjectMember.Role role : allowedRoles) {
            if (member.getRole() == role || member.getRole() == ProjectMember.Role.ADMIN) {
                authorized = true;
                break;
            }
        }
        if (!authorized) throw new RuntimeException("Action non autorisée");
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
}
