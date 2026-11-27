package com.vpf.controller;

import com.vpf.entity.User;
import com.vpf.entity.enums.UserRole;
import com.vpf.repository.UserRepository;
import com.vpf.service.BrevoEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth/admin-recovery")
@RequiredArgsConstructor
public class AdminRecoveryController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final BrevoEmailService emailService;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, OtpRecord> otps = new ConcurrentHashMap<>();

    @PostMapping("/request-otp")
    public ResponseEntity<?> requestOtp(@RequestBody Map<String, String> body) {
        String email = clean(body.get("email"));
        if (email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Enter the recovery email saved in the administrator account."));
        }
        User admin = users.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .filter(u -> u.getRecoveryEmail() != null && email.equalsIgnoreCase(u.getRecoveryEmail().trim()))
                .findFirst().orElse(null);
        if (admin == null) {
            // Avoid revealing whether an email is registered.
            return ResponseEntity.ok(Map.of("message", "If this email is registered for administrator recovery, an OTP will be sent."));
        }
        String otp = String.format("%06d", random.nextInt(1_000_000));
        try {
            emailService.sendOtp(admin.getRecoveryEmail().trim(), otp);
            otps.put(email.toLowerCase(), new OtpRecord(otp, Instant.now().plusSeconds(300), 0));
            return ResponseEntity.ok(Map.of("message", "OTP sent to the administrator recovery email. It expires in 5 minutes."));
        } catch (Exception ex) {
            return ResponseEntity.status(502).body(Map.of("message", "Could not send OTP. Check Brevo sender verification and Railway variables."));
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset(@RequestBody Map<String, String> body) {
        String email = clean(body.get("email")).toLowerCase();
        String otp = clean(body.get("otp"));
        String newUsername = clean(body.get("newUsername"));
        String newPassword = body.getOrDefault("newPassword", "");
        OtpRecord record = otps.get(email);
        if (record == null || Instant.now().isAfter(record.expiresAt) || record.attempts >= 5 || !record.otp.equals(otp)) {
            if (record != null) {
                if (record.attempts + 1 >= 5 || Instant.now().isAfter(record.expiresAt)) otps.remove(email);
                else otps.put(email, new OtpRecord(record.otp, record.expiresAt, record.attempts + 1));
            }
            return ResponseEntity.badRequest().body(Map.of("message", "OTP is invalid or expired. Request a new OTP."));
        }
        User admin = users.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .filter(u -> u.getRecoveryEmail() != null && email.equalsIgnoreCase(u.getRecoveryEmail().trim()))
                .findFirst().orElse(null);
        if (admin == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Recovery email is not registered for the administrator."));
        }
        if (newUsername.isBlank() || newUsername.length() < 3 || newPassword.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username must be at least 3 characters and password at least 8 characters."));
        }
        if (users.existsByUsername(newUsername) && !newUsername.equals(admin.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "That username is already taken."));
        }
        admin.setUsername(newUsername);
        admin.setPassword(passwordEncoder.encode(newPassword));
        users.save(admin);
        otps.remove(email);
        return ResponseEntity.ok(Map.of("message", "Administrator credentials updated. Please log in with the new username and password."));
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
    private record OtpRecord(String otp, Instant expiresAt, int attempts) {}
}
