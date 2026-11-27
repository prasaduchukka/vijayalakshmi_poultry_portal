package com.vpf.entity;

import com.vpf.entity.enums.PartyStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "supplier")
public class Supplier extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String supplierName;

    private String contactPerson;

    private String phoneNumber;

    @Column(length = 500)
    private String address;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal openingPayableBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private PartyStatus status = PartyStatus.ACTIVE;

    @Column(length = 1000)
    private String notes;
}
