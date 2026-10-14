package com.vpf.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "purchase")
public class Purchase extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    /** The vehicle trip this purchase was picked up on - shared with any Deliveries from the same trip. */
    @ManyToOne
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(nullable = false)
    private LocalDate purchaseDate;

    private Integer numberOfBirds;

    private Integer numberOfBoxes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal purchaseWeight;

    /** Optional - the quick-entry Record Purchase form no longer requires cost tracking here. */
    @Column(precision = 10, scale = 2)
    private BigDecimal purchaseRate;

    /** Optional - only creates a supplier ledger debit when present. */
    @Column(precision = 14, scale = 2)
    private BigDecimal purchaseAmount;

    @Column(length = 1000)
    private String notes;
}
