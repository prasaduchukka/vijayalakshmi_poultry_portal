package com.vpf.controller;

import com.vpf.dto.FeedRestockRequest;
import com.vpf.service.FeedInventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feed-inventory")
@RequiredArgsConstructor
public class FeedInventoryController {
    private final FeedInventoryService service;

    @GetMapping
    public Object get() { return service.get(); }

    @PostMapping("/restock")
    @PreAuthorize("hasRole('ADMIN')")
    public Object restock(@Valid @RequestBody FeedRestockRequest request) {
        return service.restock(request);
    }
}
