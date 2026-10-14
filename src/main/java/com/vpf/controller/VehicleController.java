package com.vpf.controller;

import com.vpf.dto.VehicleRequest;
import com.vpf.service.TripService;
import com.vpf.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;
    private final TripService tripService;

    @GetMapping
    public Object findAll() {
        return vehicleService.findAll();
    }

    @GetMapping("/{id}")
    public Object findById(@PathVariable Long id) {
        return vehicleService.findById(id);
    }

    /** Trip (distance/mileage) history for this vehicle, shown on the Vehicles tab. */
    @GetMapping("/{vehicleNumber}/trips")
    public Object trips(@PathVariable String vehicleNumber,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from != null && to != null) return tripService.findByVehicleAndDateRange(vehicleNumber, from, to);
        return tripService.findByVehicle(vehicleNumber);
    }

    @PostMapping
    public Object create(@Valid @RequestBody VehicleRequest req) {
        return vehicleService.create(req);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @Valid @RequestBody VehicleRequest req) {
        return vehicleService.update(id, req);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        vehicleService.delete(id);
    }
}
