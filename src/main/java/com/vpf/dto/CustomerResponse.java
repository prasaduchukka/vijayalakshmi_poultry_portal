package com.vpf.dto;

import com.vpf.entity.enums.PartyStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CustomerResponse {
    private Long id;
    private String chickenCenterName;
    private String ownerContactPerson;
    private String phoneNumber;
    private String address;
    private BigDecimal openingBalance;
    private PartyStatus status;
    private String notes;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
