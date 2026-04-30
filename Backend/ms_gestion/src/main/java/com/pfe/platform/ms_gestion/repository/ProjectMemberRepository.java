package com.pfe.platform.ms_gestion.repository;

import com.pfe.platform.ms_gestion.entity.ProjectMember;
import com.pfe.platform.ms_gestion.entity.ProjectMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {
    List<ProjectMember> findByUserId(Long userId);
    List<ProjectMember> findByProjectId(Long projectId);
    boolean existsByProjectIdAndUserId(Long projectId, Long userId);
    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);
}
