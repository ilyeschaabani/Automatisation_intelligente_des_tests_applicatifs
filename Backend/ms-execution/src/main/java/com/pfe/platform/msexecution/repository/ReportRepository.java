package com.pfe.platform.msexecution.repository;

import com.pfe.platform.msexecution.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByCampaignIdOrderByGeneratedAtDesc(Long campaignId);

    @Query("SELECT r FROM Report r JOIN Campaign c ON r.campaignId = c.id WHERE c.projectId IN :projectIds ORDER BY r.generatedAt DESC")
    List<Report> findByProjectIds(List<Long> projectIds);
}
