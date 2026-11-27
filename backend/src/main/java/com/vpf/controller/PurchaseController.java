package com.vpf.controller;

import com.vpf.dto.PurchaseRequest;
import com.vpf.service.PurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @GetMapping
    public Object findAll(@RequestParam(required = false) Long supplierId,
                           @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to) {
        if (supplierId != null) return purchaseService.findBySupplier(supplierId);
        if (from != null && to != null) return purchaseService.findByDateRange(from, to);
        return purchaseService.findAll();
    }

    @GetMapping("/{id}")
    public Object findById(@PathVariable Long id) {
        return purchaseService.findById(id);
    }

    @PostMapping
    public Object create(@Valid @RequestBody PurchaseRequest req) {
        return purchaseService.create(req);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @Valid @RequestBody PurchaseRequest req) {
        return purchaseService.update(id, req);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        purchaseService.delete(id);
    }
}
