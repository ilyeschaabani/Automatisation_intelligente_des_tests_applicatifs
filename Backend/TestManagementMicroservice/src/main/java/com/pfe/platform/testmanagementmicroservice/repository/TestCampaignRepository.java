package com.pfe.platform.testmanagementmicroservice.repository;

import com.pfe.platform.testmanagementmicroservice.entity.TestCampaign;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface TestCampaignRepository extends JpaRepository<TestCampaign, Long> {

    /**
     * Loads a campaign with its testCases initialized (avoids LazyInitializationException when open-in-view=false).
     */
    @EntityGraph(attributePaths = {"testCases"})
    Optional<TestCampaign> findWithTestCasesById(Long id);

    /**
     * Loads campaigns by project with their testCases initialized.
     */
    @EntityGraph(attributePaths = {"testCases"})
    List<TestCampaign> findByProjectId(Long projectId);

    /**
     * Loads all campaigns with their testCases initialized.
     */
    @Override
    @NonNull
    @EntityGraph(attributePaths = {"testCases"})
    List<TestCampaign> findAll();
}
