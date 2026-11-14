package com.vpf.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
public class TripResponse {
    private Long id;
    private LocalDate tripDate;
    private String vehicleNumber;
    private Integer tripNumber;
    private LocalTime startTime;
    private String companyName;
    private String supplierName;
    private BigDecimal totalWeightDispatched;
    private String driverName;
    private String helperName;
    private BigDecimal distanceKm;
    private BigDecimal mileage;
}
