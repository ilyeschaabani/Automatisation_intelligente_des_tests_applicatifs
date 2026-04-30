package com.pfe.platform.ms_gestion.repository;

import com.pfe.platform.ms_gestion.entity.Environment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnvironmentRepository extends JpaRepository<Environment, Long> {
    List<Environment> findByProjectId(Long projectId);
    boolean existsByProjectIdAndName(Long projectId, String name);
}
