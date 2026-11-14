package com.vpf.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One row of the "Continue Existing Trip" dropdown: e.g.
 * "Trip 1 - Supplier A - 1000 kg loaded - 980 kg delivered - Driver: Lambu".
 * supplierName/loadedWeight are derived from whichever Purchase(s) are linked
 * to this trip (falling back to the trip's own totalWeightDispatched, which is
 * usually set from the Delivery side when no Purchase has been recorded yet).
 */
@Data
@Builder
public class TripOptionResponse {
    private Long id;
    private Integer tripNumber;
    private LocalDate tripDate;
    private String vehicleNumber;
    private LocalTime startTime;
    private String supplierName;
    private String companyName;
    private String driverName;
    private String helperName;
    private BigDecimal loadedWeight;
    private BigDecimal deliveredWeight;
    private Long loadedBirds;
    private Long deliveredBirds;
}
