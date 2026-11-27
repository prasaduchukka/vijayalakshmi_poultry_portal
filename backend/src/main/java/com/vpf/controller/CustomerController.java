package com.vpf.controller;

import com.vpf.dto.CustomerRequest;
import com.vpf.service.CustomerService;
import com.vpf.entity.User;
import com.vpf.entity.enums.UserRole;
import com.vpf.repository.CustomerRepository;
import com.vpf.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public Object findAll() {
        return customerService.findAll();
    }

    @GetMapping("/{id}")
    public Object findById(@PathVariable Long id) {
        return customerService.findById(id);
    }

    @GetMapping("/{id}/account")
    public Object account(@PathVariable Long id) {
        return customerService.getAccountSummary(id);
    }


    /** Admin provisions or resets the dedicated login for one customer portal account. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/portal-credentials")
    public Object createPortalCredentials(@PathVariable Long id, @RequestBody Map<String, String> body) {
        var customer = customerRepository.findById(id)
                .orElseThrow(() -> new com.vpf.exception.ResourceNotFoundException("Customer not found: " + id));
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");
        if (!username.matches("^[A-Za-z0-9._-]{1,40}$")) {
            throw new IllegalArgumentException("Username must be 1-40 characters (customer number, phone number, letters, numbers, dot, underscore or hyphen).");
        }
        if (password.length() < 8 || password.length() > 72) {
            throw new IllegalArgumentException("Password must contain 8-72 characters.");
        }
        User account = userRepository.findByCustomerId(id).orElseGet(User::new);
        var existing = userRepository.findByUsername(username);
        if (existing.isPresent() && !existing.get().getId().equals(account.getId())) {
            throw new IllegalArgumentException("That username is already in use.");
        }
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(password));
        account.setFullName(customer.getChickenCenterName());
        account.setCustomerId(id);
        account.setRole(UserRole.CUSTOMER);
        account.setEnabled(true);
        userRepository.save(account);
        return Map.of("message", "Customer portal credentials saved.", "username", username);
    }

    // Any signed-in user (Admin or Gumasta) can add a new customer.
    @PostMapping
    public Object create(@Valid @RequestBody CustomerRequest req) {
        return customerService.create(req);
    }

    // Only Admin can edit an existing customer's details.
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @Valid @RequestBody CustomerRequest req) {
        return customerService.update(id, req);
    }

    // Only Admin can permanently delete a customer record.
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        userRepository.findAll().stream().filter(u -> id.equals(u.getCustomerId())).forEach(userRepository::delete);
        customerService.delete(id);
    }
}
