package com.vpf.service;

import com.vpf.dto.VehicleRequest;
import com.vpf.dto.VehicleResponse;
import com.vpf.entity.Vehicle;
import com.vpf.exception.ResourceNotFoundException;
import com.vpf.exception.BusinessRuleException;
import com.vpf.repository.TripRepository;
import org.springframework.transaction.annotation.Transactional;
import com.vpf.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;

    public VehicleResponse create(VehicleRequest req) {
        Vehicle v = new Vehicle();
        apply(v, req);
        vehicleRepository.save(v);
        return toResponse(v);
    }

    @Transactional
    public VehicleResponse update(Long id, VehicleRequest req) {
        Vehicle v = getOrThrow(id);

        String oldNumber = v.getVehicleNumber();
        String newNumber = req.getVehicleNumber() == null ? null : req.getVehicleNumber().trim();
        boolean numberChanged = oldNumber != null && newNumber != null && !oldNumber.equalsIgnoreCase(newNumber);

        if (numberChanged && hasTripHistory(v)) {
            throw new BusinessRuleException(
                    "Vehicle number cannot be changed after this vehicle has trip history. "
                    + "Historical trips must remain connected to the same vehicle. "
                    + "Create a new vehicle record for a replacement/new registration instead.");
        }

        apply(v, req);
        vehicleRepository.save(v);
        return toResponse(v);
    }

    @Transactional
    public void delete(Long id) {
        Vehicle v = getOrThrow(id);

        if (hasTripHistory(v)) {
            throw new BusinessRuleException(
                    "Vehicle \"" + v.getVehicleNumber() + "\" cannot be permanently deleted because it has existing trip/history records. "
                    + "The historical records must be preserved. If the vehicle is no longer used, keep the record and use a separate inactive/retired status instead.");
        }

        vehicleRepository.delete(v);
        vehicleRepository.flush();
    }

    /**
     * Checks both the new FK relationship and the old vehicle-number snapshot.
     * The second check protects historical trips that existed before vehicle_id
     * was introduced/migrated.
     */
    private boolean hasTripHistory(Vehicle vehicle) {
        return tripRepository.existsByVehicle_Id(vehicle.getId())
                || (vehicle.getVehicleNumber() != null
                    && tripRepository.existsByVehicleNumber(vehicle.getVehicleNumber()));
    }

    public List<VehicleResponse> findAll() {
        return vehicleRepository.findAll().stream().map(this::toResponse).toList();
    }

    public VehicleResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    public Vehicle getOrThrow(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
    }

    private void apply(Vehicle v, VehicleRequest req) {
        v.setOwnerName(req.getOwnerName());
        v.setVehicleNumber(req.getVehicleNumber().trim());
        v.setInsuranceDueDate(req.getInsuranceDueDate());
        v.setBrakeDueDate(req.getBrakeDueDate());
        v.setPermitDueDate(req.getPermitDueDate());
        v.setRoadTaxDueDate(req.getRoadTaxDueDate());
        v.setPollutionDueDate(req.getPollutionDueDate());
        v.setNotes(req.getNotes());
    }

    private VehicleResponse toResponse(Vehicle v) {
        return VehicleResponse.builder()
                .id(v.getId())
                .ownerName(v.getOwnerName())
                .vehicleNumber(v.getVehicleNumber())
                .insuranceDueDate(v.getInsuranceDueDate())
                .brakeDueDate(v.getBrakeDueDate())
                .permitDueDate(v.getPermitDueDate())
                .roadTaxDueDate(v.getRoadTaxDueDate())
                .pollutionDueDate(v.getPollutionDueDate())
                .notes(v.getNotes())
                .build();
    }
}
