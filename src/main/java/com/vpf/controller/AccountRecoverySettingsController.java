package com.vpf.controller;

import com.vpf.dto.RecoveryEmailSetupRequest;
import com.vpf.entity.User;
import com.vpf.repository.UserRepository;
import com.vpf.security.AppUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountRecoverySettingsController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PutMapping("/recovery-email")
    public ResponseEntity<?> setRecoveryEmail(@Valid @RequestBody RecoveryEmailSetupRequest request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserDetails principal)) {
            return ResponseEntity.status(401).body(Map.of("message", "Please log in again."));
        }
        User user = userRepository.findById(principal.getId()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Current password is incorrect."));
        }
        String email = request.getRecoveryEmail().trim().toLowerCase(Locale.ROOT);
        user.setRecoveryEmail(email);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Recovery email saved. Make sure you can access this inbox."));
    }
}
