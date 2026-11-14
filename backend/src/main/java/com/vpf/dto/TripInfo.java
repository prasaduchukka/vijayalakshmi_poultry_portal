package com.vpf.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Fields for brand-new trip, used inside TripSelectionRequest.newTrip when the
 * user picks "Start New Trip". vehicleNumber is required; everything else is
 * optional, including startTime - the user should never be forced to enter
 * an exact time.
 */
@Data
public class TripInfo {
    private String vehicleNumber;
    private String companyName;
    private BigDecimal totalWeightDispatched;
    private String driverName;
    private String helperName;
    private BigDecimal distanceKm;
    private BigDecimal mileage;
    private LocalTime startTime;
}
