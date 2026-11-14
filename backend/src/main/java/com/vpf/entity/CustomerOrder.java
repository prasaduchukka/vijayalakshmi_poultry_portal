package com.vpf.entity;

import com.vpf.entity.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** Named CustomerOrder (not "Order") to avoid clashing with the SQL reserved word. */
@Getter
@Setter
@Entity
@Table(name = "customer_order")
public class CustomerOrder extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private LocalDate orderDate;

    @Column(nullable = false)
    private Integer numberOfBoxes;

    private LocalDate requestedDeliveryDate;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(length = 1000)
    private String notes;
}
