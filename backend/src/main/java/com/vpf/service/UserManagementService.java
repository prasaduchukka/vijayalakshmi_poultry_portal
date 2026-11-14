package com.vpf.service;

import com.vpf.dto.UserRequest;
import com.vpf.dto.UserResponse;
import com.vpf.entity.User;
import com.vpf.entity.enums.UserRole;
import com.vpf.exception.BusinessRuleException;
import com.vpf.exception.ResourceNotFoundException;
import com.vpf.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/** Admin-only user management: creating Gumasta (staff) accounts and removing accounts. */
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    public UserResponse create(UserRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new BusinessRuleException("That username is already taken.");
        }
        User u = new User();
        u.setUsername(req.getUsername().trim());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setFullName(req.getFullName());
        u.setRole(req.getRole());
        u.setEnabled(true);
        userRepository.save(u);
        return toResponse(u);
    }

    public void delete(Long id, Long currentUserId) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        if (u.getId().equals(currentUserId)) {
            throw new BusinessRuleException("You cannot delete your own account while logged in.");
        }
        if (u.getRole() == UserRole.ADMIN) {
            long adminCount = userRepository.findAll().stream().filter(x -> x.getRole() == UserRole.ADMIN).count();
            if (adminCount <= 1) {
                throw new BusinessRuleException("Cannot delete the last remaining Admin account.");
            }
        }
        userRepository.delete(u);
    }

    private UserResponse toResponse(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .fullName(u.getFullName())
                .role(u.getRole())
                .enabled(u.isEnabled())
                .createdDate(u.getCreatedDate())
                .build();
    }
}
