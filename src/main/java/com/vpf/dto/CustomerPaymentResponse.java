package com.vpf.dto;

import com.vpf.entity.enums.PaymentMethod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class CustomerPaymentResponse {
    private Long id;
    private Long customerId;
    private String customerName;
    private LocalDate paymentDate;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private String referenceNumber;
    private String notes;
    private String createdBy;
    private LocalDateTime createdDate;
}
