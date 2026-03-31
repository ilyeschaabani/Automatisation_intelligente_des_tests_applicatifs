package com.pfe.platform.testmanagementmicroservice.repository;

import com.pfe.platform.testmanagementmicroservice.entity.Discovery;
import com.pfe.platform.testmanagementmicroservice.entity.Endpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EndpointRepository extends JpaRepository<Endpoint, Long> {
    List<Endpoint> findByDiscovery(Discovery discovery);
}
