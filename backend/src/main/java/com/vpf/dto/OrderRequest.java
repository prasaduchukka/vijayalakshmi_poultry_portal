package com.vpf.dto;

import com.vpf.entity.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;

@Data
public class OrderRequest {
    @NotNull
    private Long customerId;
    @NotNull
    private LocalDate orderDate;
    @Positive
    private Integer numberOfBoxes;
    private LocalDate requestedDeliveryDate;
    private OrderStatus status = OrderStatus.PENDING;
    private String notes;
    private String createdBy;
}
