package com.vpf.controller;

import com.vpf.dto.FarmSettingsRequest;
import com.vpf.service.FarmSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings/farm")
@RequiredArgsConstructor
public class FarmSettingsController {

    private final FarmSettingsService farmSettingsService;

    @GetMapping
    public Object get() {
        return farmSettingsService.get();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping
    public Object update(@Valid @RequestBody FarmSettingsRequest req) {
        return farmSettingsService.update(req);
    }
}
