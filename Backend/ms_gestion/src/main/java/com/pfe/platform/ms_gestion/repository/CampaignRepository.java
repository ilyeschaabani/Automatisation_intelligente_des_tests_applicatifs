package com.pfe.platform.ms_gestion.repository;

import com.pfe.platform.ms_gestion.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findByProjectId(Long projectId);

    List<Campaign> findByProjectIdIn(java.util.List<Long> projectIds);

    long countByEnvironmentId(Long environmentId);
}
