package com.vpf.dto;

import com.vpf.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SupplierPaymentRequest {
    @NotNull
    private Long supplierId;
    @NotNull
    private LocalDate paymentDate;
    @NotNull
    @Positive
    private BigDecimal amount;
    @NotNull
    private PaymentMethod paymentMethod;
    private String referenceNumber;
    private String notes;
    private String createdBy;
}
