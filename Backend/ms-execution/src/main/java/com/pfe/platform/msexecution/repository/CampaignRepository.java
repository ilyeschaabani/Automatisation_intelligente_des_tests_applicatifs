package com.pfe.platform.msexecution.repository;

import com.pfe.platform.msexecution.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
	List<Campaign> findByProjectIdOrderByStartedAtDesc(Long projectId);

	List<Campaign> findByProjectIdAndStatusInOrderByFinishedAtDesc(Long projectId, Collection<Campaign.CampaignStatus> statuses);

	long countByProjectIdAndStatus(Long projectId, Campaign.CampaignStatus status);
}