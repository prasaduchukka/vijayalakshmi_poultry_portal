package com.vpf.controller;

import com.vpf.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    /**
     * Powers the "Continue Existing Trip" dropdown on the Purchase/Delivery forms:
     * every trip already started today for this vehicle, so the user can
     * explicitly pick the right one. Also used to compute the "this will be
     * Trip N" preview when starting a new trip.
     */
    @GetMapping
    public Object findForVehicleAndDate(@RequestParam String vehicleNumber,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return tripService.findTripOptions(vehicleNumber, date);
    }

    /** Powers the "Trip Details" comparison table on the Purchases page. */
    @GetMapping("/summary")
    public Object summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return tripService.findSummaries(from, to);
    }
}
