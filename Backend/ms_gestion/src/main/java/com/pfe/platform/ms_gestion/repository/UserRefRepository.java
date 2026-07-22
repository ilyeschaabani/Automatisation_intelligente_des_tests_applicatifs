package com.pfe.platform.ms_gestion.repository;

import com.pfe.platform.ms_gestion.entity.UserRef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRefRepository extends JpaRepository<UserRef, Long> {
    List<UserRef> findByIdIn(List<Long> ids);
    java.util.Optional<UserRef> findByEmail(String email);
}
