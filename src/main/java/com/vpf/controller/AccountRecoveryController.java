package com.vpf.controller;

import com.vpf.dto.RecoveryRequest;
import com.vpf.dto.VerifyRecoveryOtpRequest;
import com.vpf.dto.ResetPasswordWithTokenRequest;
import com.vpf.dto.ResetAccountCredentialsRequest;
import com.vpf.entity.RecoveryOtp;
import com.vpf.entity.User;
import com.vpf.repository.RecoveryOtpRepository;
import com.vpf.repository.UserRepository;
import com.vpf.service.RecoveryEmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/recovery")
@RequiredArgsConstructor
public class AccountRecoveryController {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;
    private static final long OTP_TTL_MINUTES = 5;
    private static final long RESEND_WAIT_SECONDS = 60;
    private final UserRepository userRepository;
    private final RecoveryOtpRepository otpRepository;
    private final RecoveryEmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/request-otp")
    @Transactional
    public ResponseEntity<?> requestOtp(@Valid @RequestBody RecoveryRequest request) {
        String email = normalize(request.getEmail());
        LocalDateTime now = LocalDateTime.now();
        var previous = otpRepository.findTopByRecoveryEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(email, request.getPurpose());
        if (previous.isPresent() && previous.get().getCreatedAt().isAfter(now.minusSeconds(RESEND_WAIT_SECONDS))) {
            return ResponseEntity.ok(Map.of("message", "If this email is registered, an OTP will be sent. Please wait before requesting another code."));
        }
        var userOpt = userRepository.findByRecoveryEmailIgnoreCase(email).filter(User::isEnabled);
        if (userOpt.isEmpty()) {
            // Same response for unknown emails to reduce account enumeration.
            return ResponseEntity.ok(Map.of("message", "If this email is registered, an OTP will be sent."));
        }
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        RecoveryOtp record = new RecoveryOtp();
        record.setRecoveryEmail(email);
        record.setPurpose(request.getPurpose());
        record.setOtpHash(passwordEncoder.encode(otp));
        record.setCreatedAt(now);
        record.setExpiresAt(now.plusMinutes(OTP_TTL_MINUTES));
        record.setAttempts(0);
        record.setConsumed(false);
        otpRepository.save(record);
        try {
            emailService.sendOtp(email, otp, request.getPurpose() == RecoveryOtp.Purpose.USERNAME ? "username" : (request.getPurpose() == RecoveryOtp.Purpose.ACCOUNT ? "username and password" : "password"));
        } catch (RuntimeException ex) {
            otpRepository.delete(record);
            throw ex;
        }
        return ResponseEntity.ok(Map.of("message", "If this email is registered, an OTP will be sent."));
    }

    @PostMapping("/verify-otp")
    @Transactional
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyRecoveryOtpRequest request) {
        String email = normalize(request.getEmail());
        var found = otpRepository.findTopByRecoveryEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(email, request.getPurpose());
        if (found.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "Invalid or expired OTP. Request a new code."));
        RecoveryOtp record = found.get();
        LocalDateTime now = LocalDateTime.now();
        if (record.getVerifiedAt() != null) {
            return ResponseEntity.badRequest().body(Map.of("message", "This OTP has already been verified. Continue with password reset or request a new code."));
        }
        if (!record.getExpiresAt().isAfter(now) || record.getAttempts() >= MAX_ATTEMPTS) {
            record.setConsumed(true); otpRepository.save(record);
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid or expired OTP. Request a new code."));
        }
        if (!passwordEncoder.matches(request.getOtp(), record.getOtpHash())) {
            record.setAttempts(record.getAttempts() + 1);
            if (record.getAttempts() >= MAX_ATTEMPTS) record.setConsumed(true);
            otpRepository.save(record);
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid or expired OTP. Request a new code."));
        }
        if (request.getPurpose() == RecoveryOtp.Purpose.USERNAME) {
            User user = userRepository.findByRecoveryEmailIgnoreCase(email).filter(User::isEnabled).orElse(null);
            record.setConsumed(true); otpRepository.save(record);
            if (user == null) return ResponseEntity.badRequest().body(Map.of("message", "Unable to complete recovery."));
            return ResponseEntity.ok(Map.of("purpose", "USERNAME", "username", user.getUsername(), "message", "Your username was verified."));
        }
        byte[] tokenBytes = new byte[32]; RANDOM.nextBytes(tokenBytes);
        String resetToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        record.setVerifiedAt(now);
        record.setResetTokenHash(passwordEncoder.encode(resetToken));
        record.setResetTokenExpiresAt(now.plusMinutes(10));
        record.setOtpHash("VERIFIED");
        otpRepository.save(record);
        return ResponseEntity.ok(Map.of("purpose", request.getPurpose().name(), "resetToken", resetToken, "message", "OTP verified. Set your new credentials."));
    }

    @PostMapping("/reset-credentials")
    @Transactional
    public ResponseEntity<?> resetCredentials(@Valid @RequestBody ResetAccountCredentialsRequest request) {
        String email = normalize(request.getEmail());
        String newUsername = request.getNewUsername().trim();
        RecoveryOtp record = otpRepository.findTopByRecoveryEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
                email, RecoveryOtp.Purpose.ACCOUNT).orElse(null);
        if (record == null || record.getVerifiedAt() == null || record.getResetTokenHash() == null ||
                record.getResetTokenExpiresAt() == null || !record.getResetTokenExpiresAt().isAfter(LocalDateTime.now()) ||
                !passwordEncoder.matches(request.getResetToken(), record.getResetTokenHash())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Recovery session expired. Request a new OTP."));
        }

        User user = userRepository.findByRecoveryEmailIgnoreCase(email).filter(User::isEnabled).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Unable to complete recovery."));
        }
        var existingUsername = userRepository.findByUsername(newUsername).orElse(null);
        if (existingUsername != null && !existingUsername.getId().equals(user.getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "That username is already taken. Choose another username."));
        }

        user.setUsername(newUsername);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        record.setConsumed(true);
        record.setResetTokenHash(null);
        record.setOtpHash("CONSUMED");
        otpRepository.save(record);
        return ResponseEntity.ok(Map.of("message", "Username and password updated successfully. Please log in with your new credentials."));
    }

    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordWithTokenRequest request) {
        String email = normalize(request.getEmail());
        RecoveryOtp record = otpRepository.findTopByRecoveryEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(email, RecoveryOtp.Purpose.PASSWORD).orElse(null);
        if (record == null || record.getVerifiedAt() == null || record.getResetTokenHash() == null ||
                record.getResetTokenExpiresAt() == null || !record.getResetTokenExpiresAt().isAfter(LocalDateTime.now()) ||
                !passwordEncoder.matches(request.getResetToken(), record.getResetTokenHash())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Recovery session expired. Request a new OTP."));
        }
        User user = userRepository.findByRecoveryEmailIgnoreCase(email).filter(User::isEnabled).orElse(null);
        if (user == null) return ResponseEntity.badRequest().body(Map.of("message", "Unable to complete recovery."));
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        record.setConsumed(true);
        record.setResetTokenHash(null);
        record.setOtpHash("CONSUMED");
        otpRepository.save(record);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully. Please log in with your new password."));
    }

    private String normalize(String email) { return email.trim().toLowerCase(java.util.Locale.ROOT); }
}
