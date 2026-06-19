package com.pfe.platform.ms_gestion.repository;

import com.pfe.platform.ms_gestion.entity.SecurityScan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SecurityScanRepository extends JpaRepository<SecurityScan, Long> {

    List<SecurityScan> findByProjectIdOrderByStartedAtDesc(Long projectId);

    List<SecurityScan> findByScanTypeOrderByStartedAtDesc(SecurityScan.ScanType scanType);

    List<SecurityScan> findByProjectIdAndScanTypeOrderByStartedAtDesc(Long projectId, SecurityScan.ScanType scanType);

    @Query("SELECT s FROM SecurityScan s ORDER BY s.startedAt DESC")
    List<SecurityScan> findAllOrderByDate();
}
