package com.vpf.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Records when a customer (chicken center) buys chicken feed from the farm, in addition to birds. */
@Getter
@Setter
@Entity
@Table(name = "feed_sale")
public class FeedSale extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private LocalDate saleDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    private String description;

    @Column(length = 1000)
    private String notes;
}
