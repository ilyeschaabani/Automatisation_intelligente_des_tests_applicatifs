package com.pfe.platform.ms_gestion.repository;

import com.pfe.platform.ms_gestion.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByName(String name);
    boolean existsByName(String name);
}
