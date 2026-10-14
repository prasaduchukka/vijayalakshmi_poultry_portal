package com.vpf.repository;

import com.vpf.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    // NOTE: There is deliberately no findByVehicleNumberAndTripDate() returning a
    // single Trip. A vehicle can have several independent trips on the same date,
    // so that lookup is exactly the bug this redesign fixes - trip identity is
    // always the primary key (id), selected explicitly, never guessed from
    // vehicle+date. Use findByVehicleNumberAndTripDateOrderByTripNumberAsc for the
    // "existing trips today" list, and findTopByVehicleNumberAndTripDateOrderByTripNumberDesc
    // to compute the next trip number when starting a new trip.

    List<Trip> findByVehicleNumberAndTripDateOrderByTripNumberAsc(String vehicleNumber, LocalDate tripDate);

    /** True when a trip is explicitly related to this vehicle master record. */
    boolean existsByVehicle_Id(Long vehicleId);

    /** Legacy/history fallback for trips created before vehicle_id was introduced. */
    boolean existsByVehicleNumber(String vehicleNumber);

    Optional<Trip> findTopByVehicleNumberAndTripDateOrderByTripNumberDesc(String vehicleNumber, LocalDate tripDate);

    List<Trip> findByVehicleNumberOrderByTripDateDesc(String vehicleNumber);

    List<Trip> findByVehicleNumberAndTripDateBetweenOrderByTripDateAsc(String vehicleNumber, LocalDate from, LocalDate to);

    List<Trip> findAllByOrderByTripDateDesc();

    List<Trip> findByTripDateBetweenOrderByTripDateDesc(LocalDate from, LocalDate to);
}
