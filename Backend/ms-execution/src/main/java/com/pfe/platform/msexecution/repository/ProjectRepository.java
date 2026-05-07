package com.pfe.platform.msexecution.repository;

import com.pfe.platform.msexecution.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {}