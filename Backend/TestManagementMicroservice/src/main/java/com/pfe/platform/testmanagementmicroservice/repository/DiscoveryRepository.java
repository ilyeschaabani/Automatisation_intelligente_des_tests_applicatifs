package com.pfe.platform.testmanagementmicroservice.repository;

import com.pfe.platform.testmanagementmicroservice.entity.Discovery;
import com.pfe.platform.testmanagementmicroservice.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DiscoveryRepository extends JpaRepository<Discovery, Long> {
    List<Discovery> findByProject(Project project);
    Optional<Discovery> findTopByProjectIdAndBranchAndStatusOrderByCreatedAtDesc(
            Long projectId,
            String branch,
            String status
    );
    @Query("""
    SELECT d FROM Discovery d
    LEFT JOIN FETCH d.endpoints
    WHERE d.project.id = :projectId
    AND d.branch = :branch
    AND d.status = :status
    ORDER BY d.createdAt DESC
""")
    List<Discovery> findLatestWithEndpoints(Long projectId, String branch, String status);
    Optional<Discovery> findTopByProjectIdAndStatusOrderByCreatedAtDesc(Long projectId, String status);
    @Query("""
    SELECT d FROM Discovery d
    LEFT JOIN FETCH d.endpoints
    WHERE d.repoUrl = :repoUrl
    AND d.branch = :branch
    AND d.status = :status
    ORDER BY d.createdAt DESC
""")
    List<Discovery> findLatestByRepoAndBranch(String repoUrl, String branch, String status);

    @Query("SELECT d FROM Discovery d LEFT JOIN FETCH d.endpoints WHERE d.project.id = :projectId")
    Optional<Discovery> findByProjectIdWithEndpoints(@Param("projectId") Long projectId);
}



