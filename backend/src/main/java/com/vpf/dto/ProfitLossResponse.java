package com.vpf.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class ProfitLossResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal salesRevenue;
    private BigDecimal purchaseCost;
    private BigDecimal recordedExpenses;
    private BigDecimal estimatedProfitLoss;
}
