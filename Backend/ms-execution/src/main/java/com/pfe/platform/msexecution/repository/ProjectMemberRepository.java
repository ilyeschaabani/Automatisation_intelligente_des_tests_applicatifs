package com.pfe.platform.msexecution.repository;

import com.pfe.platform.msexecution.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
    boolean existsByProjectIdAndUserId(Long projectId, Long userId);
    List<ProjectMember> findByUserId(Long userId);
}
