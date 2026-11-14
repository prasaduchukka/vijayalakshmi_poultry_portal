package com.vpf.dto;

import com.vpf.entity.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderResponse {
    private Long id;
    private Long customerId;
    private String customerName;
    private LocalDate orderDate;
    private Integer numberOfBoxes;
    private LocalDate requestedDeliveryDate;
    private OrderStatus status;
    private String notes;
    private String createdBy;
    private LocalDateTime createdDate;
}
