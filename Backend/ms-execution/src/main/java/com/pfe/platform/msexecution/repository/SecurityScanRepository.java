package com.pfe.platform.msexecution.repository;

import com.pfe.platform.msexecution.entity.SecurityScan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SecurityScanRepository extends JpaRepository<SecurityScan, Long> {
    List<SecurityScan> findByProjectIdOrderByStartedAtDesc(Long projectId);
    Optional<SecurityScan> findByScanRef(String scanRef);
}
