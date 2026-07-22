package com.pfe.platform.authenticationmicroservice.Repository;

import com.pfe.platform.authenticationmicroservice.Entity.PasswordResetRequest;
import com.pfe.platform.authenticationmicroservice.Entity.PasswordResetRequest.ResetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Long> {

    List<PasswordResetRequest> findByStatusOrderByCreatedAtDesc(ResetStatus status);

    List<PasswordResetRequest> findAllByOrderByCreatedAtDesc();

    boolean existsByUserIdAndStatus(Long userId, ResetStatus status);

    void deleteAllByUserId(Long userId);
}
