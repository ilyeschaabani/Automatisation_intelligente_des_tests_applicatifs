package com.pfe.platform.testmanagementmicroservice.repository;

import com.pfe.platform.testmanagementmicroservice.entity.Discovery;
import com.pfe.platform.testmanagementmicroservice.entity.Endpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EndpointRepository extends JpaRepository<Endpoint, Long> {
    List<Endpoint> findByDiscovery(Discovery discovery);
    @Query("SELECT e FROM Endpoint e JOIN FETCH e.discovery d WHERE d.project.id = :projectId")
    List<Endpoint> findByProjectIdWithDiscovery(@Param("projectId") Long projectId);

}
