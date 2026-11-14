package com.vpf.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One vehicle's round trip on a given day: picks up from a supplier (Purchase)
 * and drops off at one or more chicken centers (Deliveries). Recording the
 * same trip info on both sides lets the owner cross-check the supplier's
 * dispatch weight against what actually got delivered.
 *
 * IMPORTANT: A trip's identity is its own primary key (id), never
 * vehicleNumber + tripDate. The same vehicle can make several independent
 * trips on the same day (different supplier, different load) - each one is
 * its own Trip row, distinguished for display purposes by tripNumber
 * (Trip 1, Trip 2, ...), which is auto-generated per vehicle+date and must
 * never be guessed/re-derived from vehicle+date lookups. See TripService.
 */
@Getter
@Setter
@Entity
@Table(name = "trip")
public class Trip extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate tripDate;

    /**
     * The actual vehicle record used for this trip. This is the authoritative
     * relationship used for vehicle history/deletion protection.
     *
     * vehicleNumber is intentionally retained as a historical/display snapshot
     * so existing reports and old data continue to work. New trips always set
     * both fields together.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Column(nullable = false)
    private String vehicleNumber;

    /**
     * Display-only sequence number (Trip 1, Trip 2, ...) for this vehicle on this date.
     * Auto-assigned - never entered by the user. Nullable at the DB level (not
     * "nullable = false") purely so adding this column doesn't fail against rows that
     * existed before this feature; every trip created going forward always gets one.
     * See migration_v3.sql for backfilling old rows.
     */
    private Integer tripNumber;

    /** Optional - the user is never required to enter this. */
    private LocalTime startTime;

    private String companyName;

    /** Total weight loaded onto the vehicle for the whole trip, before splitting across stops. */
    @Column(precision = 10, scale = 2)
    private BigDecimal totalWeightDispatched;

    private String driverName;

    private String helperName;

    @Column(precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(precision = 10, scale = 2)
    private BigDecimal mileage;

    @Column(length = 1000)
    private String notes;
}
