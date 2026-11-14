package com.vpf.controller;

import com.vpf.dto.OrderRequest;
import com.vpf.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public Object findAll(@RequestParam(required = false) Long customerId) {
        return customerId != null ? orderService.findByCustomer(customerId) : orderService.findAll();
    }

    @GetMapping("/pending")
    public Object findPending() {
        return orderService.findPending();
    }

    @PostMapping
    public Object create(@Valid @RequestBody OrderRequest req) {
        return orderService.create(req);
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @Valid @RequestBody OrderRequest req) {
        return orderService.update(id, req);
    }
}
