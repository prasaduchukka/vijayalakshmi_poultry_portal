package com.vpf.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "delivery")
public class Delivery extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    /** The vehicle trip this delivery was part of - lets purchases and deliveries cross-check. */
    @ManyToOne
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(nullable = false)
    private LocalDate deliveryDate;

    /** Optional - boxes are no longer required to record a delivery. */
    private Integer numberOfBoxes;

    /** Optional - the owner's paper ledger tracks bird count per delivery alongside weight. */
    private Integer numberOfBirds;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal dispatchWeight;

    /**
     * Optional. When present, billing uses receivedWeight (matches the original
     * confirmed rule). When absent (current default workflow), billing falls back
     * to dispatchWeight - see DeliveryService for the calculation.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal receivedWeight;

    /** Calculated only when receivedWeight is present: dispatchWeight - receivedWeight. Never a percentage. */
    @Column(precision = 10, scale = 2)
    private BigDecimal weightDifference;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal sellingRate;

    /** Calculated: billingWeight * sellingRate, where billingWeight is receivedWeight if present, else dispatchWeight. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal salesAmount;

    @Column(length = 1000)
    private String notes;
}
