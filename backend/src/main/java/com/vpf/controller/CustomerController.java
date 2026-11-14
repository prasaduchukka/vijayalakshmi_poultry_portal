package com.vpf.controller;

import com.vpf.dto.CustomerRequest;
import com.vpf.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

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
        customerService.delete(id);
    }
}
