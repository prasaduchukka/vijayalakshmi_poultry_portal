package com.vpf.dto;

import com.vpf.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DeliveryRequest {
    @NotNull
    private Long customerId;
    private Long orderId;
    @NotNull
    private LocalDate deliveryDate;
    private Integer numberOfBoxes;
    private Integer numberOfBirds;
    @NotNull
    @Positive
    private BigDecimal dispatchWeight;
    /** Optional - when omitted, billing falls back to dispatchWeight * sellingRate. */
    @Positive
    private BigDecimal receivedWeight;
    @NotNull
    @PositiveOrZero
    private BigDecimal sellingRate;
    private String notes;
    private String createdBy;

    /**
     * Optional, explicit trip choice: Start New Trip or Continue Existing Trip.
     * Stays constant (same tripId) across a Save & Next batch for one vehicle's
     * day unless the user deliberately changes it. Never inferred from vehicle+date.
     */
    private TripSelectionRequest trip;

    /** Optional - the payment section is always shown directly (no "paid at delivery" toggle). */
    private BigDecimal paymentAmount;
    private PaymentMethod paymentMethod;

    /** When true, the save operation is the final stop for the selected trip and all purchased birds must be delivered. */
    private Boolean finishTrip;
}
