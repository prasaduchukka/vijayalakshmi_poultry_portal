package com.vpf.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CustomerAccountSummary {
    private CustomerResponse customer;
    private BigDecimal currentOutstandingBalance;
    private long totalDeliveries;
    private long totalBoxes;
    private BigDecimal totalReceivedWeight;
    private BigDecimal totalSales;
    private BigDecimal totalPaid;
}
