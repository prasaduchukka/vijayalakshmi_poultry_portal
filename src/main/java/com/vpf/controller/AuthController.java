package com.vpf.controller;

import com.vpf.dto.ChangePasswordRequest;
import com.vpf.dto.LoginRequest;
import com.vpf.entity.User;
import com.vpf.repository.UserRepository;
import com.vpf.security.AppUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                    HttpServletRequest request,
                                    jakarta.servlet.http.HttpServletResponse response) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        AppUserDetails principal = (AppUserDetails) auth.getPrincipal();
        return ResponseEntity.ok(Map.of(
                "username", principal.getUsername(),
                "fullName", principal.getFullName() == null ? "" : principal.getFullName(),
                "role", principal.getAuthorities().stream().findFirst().map(Object::toString).orElse("ROLE_GUMASTA").replace("ROLE_", "")
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        request.getSession().invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        return ResponseEntity.ok(Map.of(
                "username", principal.getUsername(),
                "fullName", principal.getFullName() == null ? "" : principal.getFullName(),
                "role", principal.getAuthorities().stream().findFirst().map(Object::toString).orElse("ROLE_GUMASTA").replace("ROLE_", "")
        ));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest req, Authentication authentication) {
        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId()).orElseThrow();
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Current password is incorrect"));
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    /** Self-service username change. Invalidates the session afterward - the new username needs a fresh login. */
    @PostMapping("/change-username")
    public ResponseEntity<?> changeUsername(@Valid @RequestBody com.vpf.dto.ChangeUsernameRequest req,
                                             Authentication authentication, HttpServletRequest request) {
        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId()).orElseThrow();
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Current password is incorrect"));
        }
        String newUsername = req.getNewUsername().trim();
        if (userRepository.existsByUsername(newUsername) && !newUsername.equals(user.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "That username is already taken"));
        }
        user.setUsername(newUsername);
        userRepository.save(user);
        request.getSession().invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Username updated. Please log in again with your new username."));
    }
}
