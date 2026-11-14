package com.vpf.dto;

import com.vpf.entity.enums.PartyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustomerRequest {
    @NotBlank
    private String chickenCenterName;
    private String ownerContactPerson;
    private String phoneNumber;
    private String address;
    @PositiveOrZero
    private BigDecimal openingBalance = BigDecimal.ZERO;
    private PartyStatus status = PartyStatus.ACTIVE;
    private String notes;
}
