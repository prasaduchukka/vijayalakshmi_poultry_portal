package com.vpf.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "account_recovery_otp", indexes = {
    @Index(name = "idx_recovery_email_purpose_created", columnList = "recovery_email,purpose,created_at")
})
public class RecoveryOtp {
    public enum Purpose { USERNAME, PASSWORD, ACCOUNT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recovery_email", nullable = false, length = 254)
    private String recoveryEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Purpose purpose;

    @Column(name = "otp_hash", nullable = false, length = 100)
    private String otpHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false)
    private boolean consumed = false;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "reset_token_hash", length = 100)
    private String resetTokenHash;

    @Column(name = "reset_token_expires_at")
    private LocalDateTime resetTokenExpiresAt;
}
