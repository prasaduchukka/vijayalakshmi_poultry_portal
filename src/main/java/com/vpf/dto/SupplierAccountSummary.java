package com.vpf.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SupplierAccountSummary {
    private SupplierResponse supplier;
    private BigDecimal currentOutstandingPayable;
    private BigDecimal totalPurchased;
    private BigDecimal totalPaid;
}
