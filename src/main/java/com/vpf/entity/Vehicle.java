package com.vpf.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** Compliance/document tracker for the farm's delivery vehicles (insurance, permit, tax, etc). */
@Getter
@Setter
@Entity
@Table(name = "vehicle")
public class Vehicle extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ownerName;

    @Column(nullable = false, unique = true)
    private String vehicleNumber;

    private LocalDate insuranceDueDate;
    private LocalDate brakeDueDate;
    private LocalDate permitDueDate;
    private LocalDate roadTaxDueDate;
    private LocalDate pollutionDueDate;

    @Column(length = 1000)
    private String notes;
}
