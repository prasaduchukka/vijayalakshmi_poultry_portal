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

    /** Chicken delivery amount. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal salesAmount;

    /** Legacy column retained for compatibility with older database rows; not used by this client workflow. */
    @Column(precision = 14, scale = 2)
    private BigDecimal feedAmount;

    /** Legacy column retained for compatibility with older database rows; not used by this client workflow. */
    private Integer feedBags;

    /** Average weight loss per box in kilograms, supplied by the farm. */
    @Column(precision = 10, scale = 3)
    private BigDecimal averageWeightLoss;

    /** Number of mortality birds associated with this delivery. */
    private Integer numberOfMortality;

    /** Calculated customer compensation, persisted for a stable audit trail. */
    @Column(precision = 14, scale = 2)
    private BigDecimal weightLossAmount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    private BigDecimal mortalityAmount = BigDecimal.ZERO;

    @Transient
    public BigDecimal getTotalAmount() {
        return salesAmount == null ? BigDecimal.ZERO : salesAmount;
    }

    @Column(length = 1000)
    private String notes;
}
