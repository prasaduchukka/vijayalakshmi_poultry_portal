package com.vpf.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the "Trip Details" table on the Purchases page - lets the owner
 * compare what was loaded onto a vehicle against what actually got delivered
 * across all its stops that day.
 */
@Data
@Builder
public class TripSummaryResponse {
    private Long tripId;
    private Integer tripNumber;
    private LocalDate tripDate;
    private String vehicleNumber;
    private String companyName;
    private String supplierName;
    private BigDecimal totalLoadedWeight;
    private int customerCount;
    private BigDecimal totalDeliveredWeight;
    private Long totalLoadedBirds;
    private Long totalDeliveredBirds;
    private Long birdsDifference;
    private BigDecimal weightDifference;
    private BigDecimal totalSalesAmount;
}
