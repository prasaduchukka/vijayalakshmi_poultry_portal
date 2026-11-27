package com.vpf.controller;

import com.vpf.dto.SupplierRequest;
import com.vpf.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    public Object findAll() {
        return supplierService.findAll();
    }

    @GetMapping("/{id}")
    public Object findById(@PathVariable Long id) {
        return supplierService.findById(id);
    }

    @GetMapping("/{id}/account")
    public Object account(@PathVariable Long id) {
        return supplierService.getAccountSummary(id);
    }

    @PostMapping
    public Object create(@Valid @RequestBody SupplierRequest req) {
        return supplierService.create(req);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @Valid @RequestBody SupplierRequest req) {
        return supplierService.update(id, req);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        supplierService.delete(id);
    }
}
