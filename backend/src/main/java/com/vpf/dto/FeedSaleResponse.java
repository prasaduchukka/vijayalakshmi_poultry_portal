package com.vpf.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class FeedSaleResponse {
    private Long id;
    private Long customerId;
    private String customerName;
    private LocalDate saleDate;
    private BigDecimal amount;
    private String description;
    private String notes;
    private String createdBy;
    private LocalDateTime createdDate;
}
