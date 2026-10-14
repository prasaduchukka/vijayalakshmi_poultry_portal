package com.vpf.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
public class DeliveryResponse {
    private Long id;
    private Long customerId;
    private String customerName;
    private Long orderId;
    private LocalDate deliveryDate;
    private Integer numberOfBoxes;
    private Integer numberOfBirds;
    private BigDecimal dispatchWeight;
    private BigDecimal receivedWeight;
    private BigDecimal weightDifference;
    private BigDecimal sellingRate;
    private BigDecimal salesAmount;
    private String notes;
    private String createdBy;
    private LocalDateTime createdDate;

    // Trip/vehicle info, flattened for easy display - null if none was recorded.
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

    /** True if a payment was recorded together with this delivery. */
    private boolean paymentRecorded;
    private BigDecimal paymentAmount;
}
