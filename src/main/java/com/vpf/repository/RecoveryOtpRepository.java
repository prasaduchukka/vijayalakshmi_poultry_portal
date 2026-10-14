package com.vpf.repository;

import com.vpf.entity.RecoveryOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RecoveryOtpRepository extends JpaRepository<RecoveryOtp, Long> {
    Optional<RecoveryOtp> findTopByRecoveryEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(String email, RecoveryOtp.Purpose purpose);
    Optional<RecoveryOtp> findTopByRecoveryEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(String email, RecoveryOtp.Purpose purpose);
}
