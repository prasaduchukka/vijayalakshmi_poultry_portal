package com.vpf.controller;

import com.vpf.dto.SupplierPaymentRequest;
import com.vpf.service.SupplierPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/supplier-payments")
@RequiredArgsConstructor
public class SupplierPaymentController {

    private final SupplierPaymentService supplierPaymentService;

    @GetMapping
    public Object findBySupplier(@RequestParam Long supplierId) {
        return supplierPaymentService.findBySupplier(supplierId);
    }

    @PostMapping
    public Object create(@Valid @RequestBody SupplierPaymentRequest req) {
        return supplierPaymentService.create(req);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @Valid @RequestBody SupplierPaymentRequest req) {
        return supplierPaymentService.update(id, req);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        supplierPaymentService.delete(id);
    }
}
