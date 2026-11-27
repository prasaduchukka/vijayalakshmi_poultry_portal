package com.vpf.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
public class PurchaseResponse {
    private Long id;
    private Long supplierId;
    private String supplierName;
    private LocalDate purchaseDate;
    private Integer numberOfBirds;
    private Integer numberOfBoxes;
    private BigDecimal purchaseWeight;
    private BigDecimal purchaseRate;
    private BigDecimal purchaseAmount;
    private String notes;
    private String createdBy;
    private LocalDateTime createdDate;

    private Long tripId;
    private Integer tripNumber;
    private LocalTime startTime;
    private String vehicleNumber;
    private String companyName;
    private String driverName;
    private String helperName;
    private BigDecimal totalWeightDispatched;
    private BigDecimal distanceKm;
    private BigDecimal mileage;
}
