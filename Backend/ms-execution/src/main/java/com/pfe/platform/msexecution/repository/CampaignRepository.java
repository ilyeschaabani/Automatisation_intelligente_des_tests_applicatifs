package com.pfe.platform.msexecution.repository;

import com.pfe.platform.msexecution.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {}