package com.pfe.platform.msexecution.repository;

import com.pfe.platform.msexecution.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {}