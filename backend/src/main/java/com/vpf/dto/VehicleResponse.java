package com.vpf.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class VehicleResponse {
    private Long id;
    private String ownerName;
    private String vehicleNumber;
    private LocalDate insuranceDueDate;
    private LocalDate brakeDueDate;
    private LocalDate permitDueDate;
    private LocalDate roadTaxDueDate;
    private LocalDate pollutionDueDate;
    private String notes;
}
