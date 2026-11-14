package com.vpf.dto;

import lombok.Data;

/**
 * Embedded in PurchaseRequest/DeliveryRequest as "trip". Represents the
 * user's EXPLICIT choice on the "Start New Trip" / "Continue Existing Trip"
 * control - exactly one of the two fields below should be set:
 *
 *   - tripId set   -> "Continue Existing Trip": use this exact trip, as-is.
 *                     Never re-searched, never overwritten.
 *   - newTrip set  -> "Start New Trip": always creates a brand new Trip row,
 *                     even if the same vehicle/date/supplier already has one.
 *   - both null    -> no trip/vehicle tracking for this record (unchanged
 *                     from the original optional-trip behavior).
 *
 * The backend must never fall back to a vehicle+date lookup when neither (or
 * both) of these is unambiguous - see TripService.resolve().
 */
@Data
public class TripSelectionRequest {
    private Long tripId;
    private TripInfo newTrip;
}
