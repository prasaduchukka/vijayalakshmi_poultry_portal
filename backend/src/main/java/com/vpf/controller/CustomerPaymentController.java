package com.vpf.controller;

import com.vpf.dto.CustomerPaymentRequest;
import com.vpf.service.CustomerPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer-payments")
@RequiredArgsConstructor
public class CustomerPaymentController {

    private final CustomerPaymentService customerPaymentService;

    @GetMapping
    public Object findByCustomer(@RequestParam Long customerId) {
        return customerPaymentService.findByCustomer(customerId);
    }

    @PostMapping
    public Object create(@Valid @RequestBody CustomerPaymentRequest req) {
        return customerPaymentService.create(req);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @Valid @RequestBody CustomerPaymentRequest req) {
        return customerPaymentService.update(id, req);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        customerPaymentService.delete(id);
    }
}
