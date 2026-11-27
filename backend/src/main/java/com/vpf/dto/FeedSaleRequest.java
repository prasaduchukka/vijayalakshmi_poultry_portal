package com.vpf.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.time.LocalDate;

@Data
public class FeedSaleRequest {
    @NotNull private Long customerId;
    @NotNull private LocalDate saleDate;
    @NotNull @Positive private Integer bagCount;
    private java.math.BigDecimal amountPaid;
    private String description;
    private String notes;
    private String createdBy;
}
