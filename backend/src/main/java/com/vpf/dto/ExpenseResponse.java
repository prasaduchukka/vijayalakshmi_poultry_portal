package com.vpf.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ExpenseResponse {
    private Long id;
    private LocalDate expenseDate;
    private String category;
    private BigDecimal amount;
    private String description;
    private String notes;
    private String createdBy;
    private LocalDateTime createdDate;
}
