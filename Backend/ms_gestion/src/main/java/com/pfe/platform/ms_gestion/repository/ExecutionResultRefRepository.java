package com.pfe.platform.ms_gestion.repository;

import com.pfe.platform.ms_gestion.entity.ExecutionResultRef;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionResultRefRepository extends JpaRepository<ExecutionResultRef, Long> {

    java.util.List<ExecutionResultRef> findByAssignedToUserIdOrderByIdDesc(Long assignedToUserId);
}
