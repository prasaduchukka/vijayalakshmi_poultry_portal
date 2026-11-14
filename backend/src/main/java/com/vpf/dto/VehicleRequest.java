package com.vpf.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class VehicleRequest {
    private String ownerName;
    @NotBlank
    private String vehicleNumber;
    private LocalDate insuranceDueDate;
    private LocalDate brakeDueDate;
    private LocalDate permitDueDate;
    private LocalDate roadTaxDueDate;
    private LocalDate pollutionDueDate;
    private String notes;
}
