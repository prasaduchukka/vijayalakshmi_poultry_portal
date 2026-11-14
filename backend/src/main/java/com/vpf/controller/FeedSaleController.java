package com.vpf.controller;

import com.vpf.dto.FeedSaleRequest;
import com.vpf.service.FeedSaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feed-sales")
@RequiredArgsConstructor
public class FeedSaleController {

    private final FeedSaleService feedSaleService;

    @GetMapping
    public Object findByCustomer(@RequestParam Long customerId) {
        return feedSaleService.findByCustomer(customerId);
    }

    @PostMapping
    public Object create(@Valid @RequestBody FeedSaleRequest req) {
        return feedSaleService.create(req);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @Valid @RequestBody FeedSaleRequest req) {
        return feedSaleService.update(id, req);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        feedSaleService.delete(id);
    }
}
