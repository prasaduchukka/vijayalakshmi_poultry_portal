package com.vpf.dto;

import com.vpf.entity.enums.LedgerReferenceType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class LedgerEntryResponse {
    private Long id;
    private LocalDate entryDate;
    private LedgerReferenceType referenceType;
    private Long referenceId;
    private BigDecimal debit;
    private BigDecimal credit;
    private BigDecimal balanceAfter;
    private String description;
}
