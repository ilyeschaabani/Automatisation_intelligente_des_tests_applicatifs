package com.pfe.platform.msexecution.repository;

import com.pfe.platform.msexecution.entity.Environment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentRepository extends JpaRepository<Environment, Long> {}