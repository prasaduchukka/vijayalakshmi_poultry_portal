package com.vpf.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FeedSaleRequest {
    @NotNull
    private Long customerId;
    @NotNull
    private LocalDate saleDate;
    @NotNull
    @Positive
    private BigDecimal amount;
    private String description;
    private String notes;
    private String createdBy;
}
