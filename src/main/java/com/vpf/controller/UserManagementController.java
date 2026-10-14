package com.vpf.controller;

import com.vpf.dto.UserRequest;
import com.vpf.security.AppUserDetails;
import com.vpf.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Admin-only: create/remove Gumasta (staff) and Admin accounts. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementController {

    private final UserManagementService userManagementService;

    @GetMapping
    public Object findAll() {
        return userManagementService.findAll();
    }

    @PostMapping
    public Object create(@Valid @RequestBody UserRequest req) {
        return userManagementService.create(req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, Authentication authentication) {
        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        userManagementService.delete(id, principal.getId());
    }
}
