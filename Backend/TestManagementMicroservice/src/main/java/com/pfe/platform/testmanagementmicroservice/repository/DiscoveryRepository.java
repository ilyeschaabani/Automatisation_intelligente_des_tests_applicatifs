package com.pfe.platform.testmanagementmicroservice.repository;

import com.pfe.platform.testmanagementmicroservice.entity.Discovery;
import com.pfe.platform.testmanagementmicroservice.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiscoveryRepository extends JpaRepository<Discovery, Long> {
    List<Discovery> findByProject(Project project);
    Optional<Discovery> findTopByProjectIdAndBranchAndStatusOrderByCreatedAtDesc(
            Long projectId,
            String branch,
            String status
    );
    Optional<Discovery> findTopByProjectIdAndStatusOrderByCreatedAtDesc(Long projectId, String status);
}



