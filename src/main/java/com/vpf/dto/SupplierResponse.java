package com.vpf.dto;

import com.vpf.entity.enums.PartyStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SupplierResponse {
    private Long id;
    private String supplierName;
    private String contactPerson;
    private String phoneNumber;
    private String address;
    private BigDecimal openingPayableBalance;
    private PartyStatus status;
    private String notes;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
