package com.vpf.service;

import com.vpf.dto.TripInfo;
import com.vpf.dto.TripOptionResponse;
import com.vpf.dto.TripResponse;
import com.vpf.dto.TripSelectionRequest;
import com.vpf.entity.Purchase;
import com.vpf.entity.Delivery;
import com.vpf.entity.Trip;
import com.vpf.entity.CustomerOrder;
import com.vpf.entity.enums.OrderStatus;
import com.vpf.exception.BusinessRuleException;
import com.vpf.exception.ResourceNotFoundException;
import com.vpf.repository.DeliveryRepository;
import com.vpf.repository.PurchaseRepository;
import com.vpf.repository.TripRepository;
import com.vpf.repository.VehicleRepository;
import com.vpf.repository.CustomerOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A vehicle can make several independent trips in one day (different
 * supplier, different load) - a Trip's identity is always its own primary
 * key, never vehicleNumber + tripDate. This service enforces that:
 *
 *   - resolve(...) is the ONLY way Purchase/Delivery attach to a Trip. It
 *     either uses an explicitly-selected existing trip id as-is, or creates a
 *     brand new trip. It never searches for "the" trip by vehicle+date and
 *     never mutates a trip other than the one just created.
 *   - createNewTrip(...) always INSERTs a new row and auto-assigns the next
 *     tripNumber for that vehicle+date (Trip 1, Trip 2, ...). The user never
 *     types the trip number themselves, so there's no chance of a duplicate.
 */
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final VehicleRepository vehicleRepository;
    private final DeliveryRepository deliveryRepository;
    private final PurchaseRepository purchaseRepository;
    private final LedgerService ledgerService;
    private final CustomerPaymentService customerPaymentService;
    private final CustomerOrderRepository customerOrderRepository;

    /**
     * Resolves a Purchase/Delivery's trip from the user's explicit choice.
     * Returns null if no trip/vehicle tracking was requested at all.
     */
    public Trip resolve(LocalDate date, TripSelectionRequest selection) {
        if (selection == null) {
            return null;
        }
        if (selection.getTripId() != null && selection.getNewTrip() != null) {
            throw new BusinessRuleException("Choose either 'Start New Trip' or 'Continue Existing Trip', not both.");
        }
        if (selection.getTripId() != null) {
            // Continue Existing Trip: use only the explicitly selected trip.
            Trip existing = tripRepository.findById(selection.getTripId())
                    .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + selection.getTripId()));
            if (!date.equals(existing.getTripDate())) {
                throw new BusinessRuleException("The selected trip belongs to " + existing.getTripDate() + ". Select a trip for " + date + ".");
            }
            return existing;
        }
        if (selection.getNewTrip() != null) {
            TripInfo info = selection.getNewTrip();
            if (info.getVehicleNumber() == null || info.getVehicleNumber().isBlank()) {
                throw new BusinessRuleException("Vehicle number is required to start a new trip.");
            }
            return createNewTrip(date, info);
        }
        return null;
    }

    /** Always creates a brand-new Trip row - never finds/updates an existing one. */
    public Trip createNewTrip(LocalDate date, TripInfo info) {
        String vehicleNumber = info.getVehicleNumber().trim();

        // A trip must always point to a real vehicle master record.
        // Keeping the vehicleNumber snapshot as well preserves existing reports
        // and historical data while the FK establishes the real relationship.
        com.vpf.entity.Vehicle vehicle = vehicleRepository.findByVehicleNumber(vehicleNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vehicle not found: " + vehicleNumber + ". Select a registered vehicle."));

        Integer nextTripNumber = tripRepository
                .findTopByVehicleNumberAndTripDateOrderByTripNumberDesc(vehicleNumber, date)
                .map(t -> t.getTripNumber() + 1)
                .orElse(1);

        Trip t = new Trip();
        t.setVehicle(vehicle);
        t.setVehicleNumber(vehicleNumber);
        t.setTripDate(date);
        t.setTripNumber(nextTripNumber);
        t.setStartTime(info.getStartTime());
        if (info.getCompanyName() != null && !info.getCompanyName().isBlank()) t.setCompanyName(info.getCompanyName());
        t.setTotalWeightDispatched(info.getTotalWeightDispatched());
        if (info.getDriverName() != null && !info.getDriverName().isBlank()) t.setDriverName(info.getDriverName());
        if (info.getHelperName() != null && !info.getHelperName().isBlank()) t.setHelperName(info.getHelperName());
        t.setDistanceKm(info.getDistanceKm());
        t.setMileage(info.getMileage());
        return tripRepository.save(t);
    }

    public long purchaseCount(Long tripId) {
        return purchaseRepository.findByTripId(tripId).size();
    }

    /**
     * Permanently removes a trip and everything that belongs to that trip.
     * This is used when the last Purchase linked to a trip is deleted.
     *
     * Deliveries are removed first because they reference the trip. Their
     * customer-ledger entries are also removed/recalculated so customer
     * balances and reports do not retain deleted delivery amounts.
     */
    public List<Delivery> deliveriesForTrip(Long tripId) {
        return deliveryRepository.findByTripId(tripId);
    }

    @Transactional
    public void deleteTripAndDeliveries(Long tripId) {
        Trip trip = getOrThrow(tripId);

        List<Delivery> deliveries = deliveryRepository.findByTripId(tripId);

        for (Delivery delivery : deliveries) {
            // Delivery-linked customer payments are children of the delivery.
            // Remove them before the delivery itself so no payment survives a
            // deleted purchase/trip.
            customerPaymentService.deleteForDelivery(delivery.getId());
            ledgerService.deleteCustomerLedgerEntryAndRecalculate(
                    delivery.getCustomer(),
                    com.vpf.entity.enums.LedgerReferenceType.DELIVERY,
                    delivery.getId());
        }

        if (!deliveries.isEmpty()) {
            java.util.Set<Long> orderIds = deliveries.stream()
                    .map(Delivery::getOrder)
                    .filter(java.util.Objects::nonNull)
                    .map(CustomerOrder::getId)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            deliveryRepository.deleteAll(deliveries);
            deliveryRepository.flush();
            for (Long orderId : orderIds) {
                boolean stillDelivered = !deliveryRepository.findByOrderId(orderId).isEmpty();
                if (!stillDelivered) {
                    customerOrderRepository.findById(orderId).ifPresent(order -> {
                        if (order.getStatus() != OrderStatus.CANCELLED) {
                            order.setStatus(OrderStatus.PENDING);
                            customerOrderRepository.save(order);
                        }
                    });
                }
            }
        }

        tripRepository.delete(trip);
        tripRepository.flush();
    }

    public Trip getOrThrow(Long id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + id));
    }

    /**
     * The "Continue Existing Trip" dropdown: every trip already started today
     * for this vehicle, with enough context (supplier, loaded/delivered
     * weight, driver) for the user to pick the right one with confidence.
     */
    public List<TripOptionResponse> findTripOptions(String vehicleNumber, LocalDate date) {
        return tripRepository.findByVehicleNumberAndTripDateOrderByTripNumberAsc(vehicleNumber, date)
                .stream()
                .filter(trip -> !purchaseRepository.findByTripId(trip.getId()).isEmpty())
                .map(this::toOption)
                .toList();
    }

    private TripOptionResponse toOption(Trip trip) {
        List<Purchase> purchases = purchaseRepository.findByTripId(trip.getId());
        String supplierName = purchases.stream()
                .map(p -> p.getSupplier().getSupplierName())
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
        BigDecimal purchaseWeight = purchases.stream()
                .map(Purchase::getPurchaseWeight)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal loadedWeight = trip.getTotalWeightDispatched() != null
                ? trip.getTotalWeightDispatched()
                : (purchases.isEmpty() ? null : purchaseWeight);

        BigDecimal deliveredWeight = deliveryRepository.findByTripId(trip.getId()).stream()
                .map(com.vpf.entity.Delivery::getDispatchWeight)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Long loadedBirds = totalPurchasedBirds(purchases);
        Long deliveredBirds = totalDeliveredBirds(trip.getId());

        return TripOptionResponse.builder()
                .id(trip.getId())
                .tripNumber(trip.getTripNumber())
                .tripDate(trip.getTripDate())
                .vehicleNumber(trip.getVehicleNumber())
                .startTime(trip.getStartTime())
                .supplierName(supplierName)
                .companyName(trip.getCompanyName())
                .driverName(trip.getDriverName())
                .helperName(trip.getHelperName())
                .loadedWeight(loadedWeight)
                .deliveredWeight(deliveredWeight)
                .loadedBirds(loadedBirds)
                .deliveredBirds(deliveredBirds)
                .build();
    }

    /**
     * The effective loaded weight used for the 5kg delivery tolerance check:
     * the trip's own totalWeightDispatched if set, else the sum of purchase
     * weight(s) linked to this trip, else null (unknown - can't validate).
     */
    public BigDecimal effectiveLoadedWeight(Trip trip) {
        if (trip.getTotalWeightDispatched() != null) {
            return trip.getTotalWeightDispatched();
        }
        List<Purchase> purchases = purchaseRepository.findByTripId(trip.getId());
        if (purchases.isEmpty()) {
            return null;
        }
        return purchases.stream().map(Purchase::getPurchaseWeight).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Total birds purchased for a trip. Returns null when at least one linked purchase has no bird count, because an exact comparison would then be unknowable. */
    public Long effectiveLoadedBirds(Trip trip) {
        List<Purchase> purchases = purchaseRepository.findByTripId(trip.getId());
        if (purchases.isEmpty() || purchases.stream().anyMatch(p -> p.getNumberOfBirds() == null)) return null;
        return totalPurchasedBirds(purchases);
    }

    public Long totalDeliveredBirds(Long tripId) {
        return deliveryRepository.findByTripId(tripId).stream()
                .map(com.vpf.entity.Delivery::getNumberOfBirds)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
    }

    private Long totalPurchasedBirds(List<Purchase> purchases) {
        if (purchases.isEmpty() || purchases.stream().anyMatch(p -> p.getNumberOfBirds() == null)) return null;
        return purchases.stream().map(Purchase::getNumberOfBirds).mapToLong(Integer::longValue).sum();
    }

    public List<TripResponse> findByVehicle(String vehicleNumber) {
        return tripRepository.findByVehicleNumberOrderByTripDateDesc(vehicleNumber).stream().map(this::toResponse).toList();
    }

    public List<TripResponse> findByVehicleAndDateRange(String vehicleNumber, LocalDate from, LocalDate to) {
        return tripRepository.findByVehicleNumberAndTripDateBetweenOrderByTripDateAsc(vehicleNumber, from, to)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Powers the "Trip Details" table on the Purchases page: for each trip in the
     * date range, compares the weight loaded onto the vehicle (from the purchase
     * side) against the total actually delivered across every chicken center stop.
     */
    public List<com.vpf.dto.TripSummaryResponse> findSummaries(LocalDate from, LocalDate to) {
        List<Trip> trips = (from != null && to != null)
                ? tripRepository.findByTripDateBetweenOrderByTripDateDesc(from, to)
                : tripRepository.findAllByOrderByTripDateDesc();

        return trips.stream().map(trip -> {
            List<com.vpf.entity.Delivery> deliveries = deliveryRepository.findByTripId(trip.getId());
            BigDecimal delivered = deliveries.stream()
                    .map(com.vpf.entity.Delivery::getDispatchWeight)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal sales = deliveries.stream()
                    .map(com.vpf.entity.Delivery::getSalesAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Long loadedBirds = effectiveLoadedBirds(trip);
            Long deliveredBirds = totalDeliveredBirds(trip.getId());
            long customerCount = deliveries.stream().map(d -> d.getCustomer().getId()).distinct().count();
            BigDecimal loaded = effectiveLoadedWeight(trip);
            String supplierNames = supplierNamesForTrip(trip.getId());

            return com.vpf.dto.TripSummaryResponse.builder()
                    .tripId(trip.getId())
                    .tripNumber(trip.getTripNumber())
                    .tripDate(trip.getTripDate())
                    .vehicleNumber(trip.getVehicleNumber())
                    .companyName(trip.getCompanyName())
                    .supplierName(supplierNames)
                    .totalLoadedWeight(loaded)
                    .customerCount((int) customerCount)
                    .totalDeliveredWeight(delivered)
                    .weightDifference(loaded != null ? loaded.subtract(delivered) : null)
                    .totalLoadedBirds(loadedBirds)
                    .totalDeliveredBirds(deliveredBirds)
                    .birdsDifference(loadedBirds != null ? loadedBirds - deliveredBirds : null)
                    .totalSalesAmount(sales)
                    .build();
        }).toList();
    }

    /** Returns the distinct supplier names associated with purchases on this trip. */
    private String supplierNamesForTrip(Long tripId) {
        return purchaseRepository.findByTripId(tripId).stream()
                .map(Purchase::getSupplier)
                .filter(java.util.Objects::nonNull)
                .map(s -> s.getSupplierName())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
    }

    public TripResponse toResponse(Trip t) {
        String supplierNames = supplierNamesForTrip(t.getId());

        return TripResponse.builder()
                .id(t.getId())
                .tripDate(t.getTripDate())
                .vehicleNumber(t.getVehicleNumber())
                .tripNumber(t.getTripNumber())
                .startTime(t.getStartTime())
                .companyName(t.getCompanyName())
                .supplierName(supplierNames)
                .totalWeightDispatched(t.getTotalWeightDispatched())
                .driverName(t.getDriverName())
                .helperName(t.getHelperName())
                .distanceKm(t.getDistanceKm())
                .mileage(t.getMileage())
                .build();
    }
}
