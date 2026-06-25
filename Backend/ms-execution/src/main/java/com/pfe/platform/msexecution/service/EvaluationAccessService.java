package com.pfe.platform.msexecution.service;

import com.pfe.platform.msexecution.entity.EvaluationMember;
import com.pfe.platform.msexecution.repository.EvaluationMemberRepository;
import com.pfe.platform.msexecution.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EvaluationAccessService {

    private final EvaluationMemberRepository evaluationMemberRepository;

    public void checkAccess(Long evaluationId) {
        if (SecurityUtils.isGlobalAdmin()) return;
        List<EvaluationMember> members = evaluationMemberRepository.findByEvaluationId(evaluationId);
        if (members.isEmpty()) return;
        Long userId = SecurityUtils.getCurrentUserId();
        if (members.stream().noneMatch(m -> m.getUserId().equals(userId))) {
            throw new RuntimeException("Vous n'êtes pas membre de cette évaluation");
        }
    }

    public void checkRole(Long evaluationId, EvaluationMember.Role... allowedRoles) {
        if (SecurityUtils.isGlobalAdmin()) return;
        Long userId = SecurityUtils.getCurrentUserId();
        EvaluationMember member = evaluationMemberRepository.findByEvaluationId(evaluationId)
                .stream()
                .filter(m -> m.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Vous n'êtes pas membre de cette évaluation"));
        boolean authorized = false;
        for (EvaluationMember.Role role : allowedRoles) {
            if (member.getRole() == role || member.getRole() == EvaluationMember.Role.OWNER) {
                authorized = true;
                break;
            }
        }
        if (!authorized) throw new RuntimeException("Action non autorisée pour cette évaluation");
    }

    public List<Long> getAccessibleEvaluationIds() {
        if (SecurityUtils.isGlobalAdmin()) return null;
        Long userId = SecurityUtils.getCurrentUserId();
        return evaluationMemberRepository.findByUserId(userId).stream()
                .map(EvaluationMember::getEvaluationId).toList();
    }
}
