package com.vpf.entity;

import com.vpf.entity.enums.LedgerReferenceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row per event that changes what a customer owes.
 * DELIVERY entries increase the balance (debit), PAYMENT entries decrease it (credit).
 * balanceAfter is a snapshot so the ledger table can be displayed without recomputation.
 */
@Getter
@Setter
@Entity
@Table(name = "customer_ledger_entry")
public class CustomerLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerReferenceType referenceType;

    /** id of the Delivery / CustomerPayment row this entry was generated from (null for OPENING) */
    private Long referenceId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balanceAfter;

    private String description;

    @Column(updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    protected void onCreate() {
        this.createdDate = LocalDateTime.now();
    }
}
