package com.vpf.service;

import com.vpf.dto.DeliveryRequest;
import com.vpf.dto.DeliveryResponse;
import com.vpf.entity.Customer;
import com.vpf.entity.CustomerOrder;
import com.vpf.entity.Delivery;
import com.vpf.entity.Trip;
import com.vpf.entity.enums.LedgerReferenceType;
import com.vpf.entity.enums.OrderStatus;
import com.vpf.exception.BusinessRuleException;
import com.vpf.exception.ResourceNotFoundException;
import com.vpf.repository.CustomerOrderRepository;
import com.vpf.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Implements the confirmed billing rule, with a fallback for the simplified
 * current workflow:
 *   - If Received Weight is provided: Weight Difference = Dispatch - Received (KG only),
 *     and Sales Amount = Received Weight * Selling Rate (original confirmed rule).
 *   - If Received Weight is NOT provided (current default entry flow): Sales Amount =
 *     Dispatch Weight * Selling Rate, and Weight Difference is left blank.
 * A delivery can optionally be linked to a vehicle's trip, but only by
 * CONTINUING a trip already started (with a purchase) under Purchases - see
 * resolveExistingPurchaseTrip. It can also carry a payment recorded in
 * the same request. Cumulative deliveries against a trip must not exceed the
 * trip's loaded weight by more than a 5kg tolerance (see checkDeliveryTolerance).
 */
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static final BigDecimal DELIVERY_TOLERANCE_KG = new BigDecimal("5");

    private final DeliveryRepository deliveryRepository;
    private final CustomerOrderRepository orderRepository;
    private final CustomerService customerService;
    private final LedgerService ledgerService;
    private final TripService tripService;
    private final CustomerPaymentService customerPaymentService;

    @Transactional
    public DeliveryResponse create(DeliveryRequest req) {
        Customer customer = customerService.getOrThrow(req.getCustomerId());

        if (req.getReceivedWeight() != null && req.getReceivedWeight().compareTo(req.getDispatchWeight()) > 0) {
            throw new BusinessRuleException("Received weight cannot be greater than dispatch weight.");
        }

        Delivery d = new Delivery();
        d.setCustomer(customer);

        if (req.getOrderId() != null) {
            CustomerOrder order = orderRepository.findById(req.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + req.getOrderId()));
            d.setOrder(order);
            if (order.getStatus() != OrderStatus.CANCELLED) {
                order.setStatus(OrderStatus.DELIVERED);
                orderRepository.save(order);
            }
        }

        Trip trip = resolveExistingPurchaseTrip(req.getDeliveryDate(), req.getTrip());
        checkDeliveryTolerance(trip, req.getDispatchWeight(), null);
        checkBirdCount(trip, req.getNumberOfBirds(), null);
        d.setTrip(trip);

        d.setDeliveryDate(req.getDeliveryDate());
        d.setNumberOfBoxes(req.getNumberOfBoxes());
        d.setNumberOfBirds(req.getNumberOfBirds());
        d.setDispatchWeight(req.getDispatchWeight());
        d.setReceivedWeight(req.getReceivedWeight());
        d.setSellingRate(req.getSellingRate());

        BigDecimal billingWeight;
        if (req.getReceivedWeight() != null) {
            d.setWeightDifference(req.getDispatchWeight().subtract(req.getReceivedWeight()).setScale(2, RoundingMode.HALF_UP));
            billingWeight = req.getReceivedWeight();
        } else {
            d.setWeightDifference(null);
            billingWeight = req.getDispatchWeight();
        }

        d.setSalesAmount(billingWeight.multiply(req.getSellingRate()).setScale(2, RoundingMode.HALF_UP));

        d.setNotes(req.getNotes());
        d.setCreatedBy(req.getCreatedBy());
        deliveryRepository.save(d);

        ledgerService.recordCustomerDebit(customer, req.getDeliveryDate(), LedgerReferenceType.DELIVERY,
                d.getId(), d.getSalesAmount(),
                "Delivery #" + d.getId() + " @ Rs." + d.getSellingRate() + "/kg");

        if (req.getPaymentAmount() != null && req.getPaymentAmount().compareTo(BigDecimal.ZERO) > 0) {
            customerPaymentService.createForDelivery(customer, d, req.getPaymentAmount(), req.getPaymentMethod(), req.getCreatedBy());
        }

        return toResponse(d);
    }

    /** Admin-only edit. Re-links the trip and recalculates every affected ledger. */
    @Transactional
    public DeliveryResponse update(Long id, DeliveryRequest req) {
        Delivery d = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found: " + id));

        if (req.getReceivedWeight() != null && req.getReceivedWeight().compareTo(req.getDispatchWeight()) > 0) {
            throw new BusinessRuleException("Received weight cannot be greater than dispatch weight.");
        }

        Customer oldCustomer = d.getCustomer();
        Customer customer = customerService.getOrThrow(req.getCustomerId());
        CustomerOrder oldOrder = d.getOrder();

        Trip trip = resolveExistingPurchaseTrip(req.getDeliveryDate(), req.getTrip());
        checkDeliveryTolerance(trip, req.getDispatchWeight(), d.getId());
        checkBirdCount(trip, req.getNumberOfBirds(), d.getId());

        // Resolve the new order explicitly. Never leave the old order marked DELIVERED
        // after the delivery has been moved elsewhere.
        CustomerOrder newOrder = oldOrder;
        if (req.getOrderId() != null) {
            newOrder = orderRepository.findById(req.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + req.getOrderId()));
            if (newOrder.getStatus() != OrderStatus.CANCELLED) {
                newOrder.setStatus(OrderStatus.DELIVERED);
            }
        }

        d.setCustomer(customer);
        d.setOrder(newOrder);
        d.setTrip(trip);
        d.setDeliveryDate(req.getDeliveryDate());
        d.setNumberOfBoxes(req.getNumberOfBoxes());
        d.setNumberOfBirds(req.getNumberOfBirds());
        d.setDispatchWeight(req.getDispatchWeight());
        d.setReceivedWeight(req.getReceivedWeight());
        d.setSellingRate(req.getSellingRate());

        BigDecimal billingWeight;
        if (req.getReceivedWeight() != null) {
            d.setWeightDifference(req.getDispatchWeight().subtract(req.getReceivedWeight()).setScale(2, RoundingMode.HALF_UP));
            billingWeight = req.getReceivedWeight();
        } else {
            d.setWeightDifference(null);
            billingWeight = req.getDispatchWeight();
        }
        d.setSalesAmount(billingWeight.multiply(req.getSellingRate()).setScale(2, RoundingMode.HALF_UP));
        customerPaymentService.validateDeliveryPaymentTotal(d.getId(), d.getSalesAmount());
        d.setNotes(req.getNotes());
        deliveryRepository.save(d);

        // Remove the old delivery debit if the customer changed; otherwise replace it
        // so a changed date/amount cannot leave stale ledger data.
        if (!oldCustomer.getId().equals(customer.getId())) {
            ledgerService.deleteCustomerLedgerEntryAndRecalculate(oldCustomer, LedgerReferenceType.DELIVERY, d.getId());
            ledgerService.replaceCustomerEntry(customer, LedgerReferenceType.DELIVERY, d.getId(),
                    req.getDeliveryDate(), d.getSalesAmount(), null,
                    "Delivery #" + d.getId() + " @ Rs." + d.getSellingRate() + "/kg");
        } else {
            ledgerService.replaceCustomerEntry(customer, LedgerReferenceType.DELIVERY, d.getId(),
                    req.getDeliveryDate(), d.getSalesAmount(), null,
                    "Delivery #" + d.getId() + " @ Rs." + d.getSellingRate() + "/kg");
        }

        // A payment made specifically for this delivery follows the delivery to the
        // new customer and new date. It is NOT left against the old customer.
        customerPaymentService.reassignDeliveryPayments(d.getId(), customer, req.getDeliveryDate());

        if (oldOrder != null && (newOrder == null || !oldOrder.getId().equals(newOrder.getId()))) {
            restoreOrderIfNoOtherDelivery(oldOrder);
        }
        if (newOrder != null) {
            orderRepository.save(newOrder);
        }

        return toResponse(d);
    }

    private void restoreOrderIfNoOtherDelivery(CustomerOrder order) {
        if (order == null || order.getId() == null || order.getStatus() == OrderStatus.CANCELLED) return;
        boolean stillDelivered = !deliveryRepository.findByOrderId(order.getId()).isEmpty();
        if (!stillDelivered) {
            order.setStatus(OrderStatus.PENDING);
            orderRepository.save(order);
        }
    }

    /**
     * A delivery may optionally be linked to a trip (when a vehicle is picked),
     * but it can only ever CONTINUE a trip that already has a purchase recorded
     * against it - it can never start a new one. No vehicle selected at all
     * (selection == null) is still perfectly valid; it just means this delivery
     * isn't trip-tracked.
     */
    private Trip resolveExistingPurchaseTrip(LocalDate deliveryDate, com.vpf.dto.TripSelectionRequest selection) {
        if (selection == null) {
            return null;
        }
        if (selection.getNewTrip() != null) {
            throw new BusinessRuleException("A delivery can only continue an existing trip created from a Purchase - it cannot start a new one.");
        }
        if (selection.getTripId() == null) {
            throw new BusinessRuleException("Select an existing trip for this vehicle, or leave Vehicle set to None.");
        }
        Trip trip = tripService.resolve(deliveryDate, selection); // also enforces trip.tripDate == deliveryDate
        if (tripService.purchaseCount(trip.getId()) == 0) {
            throw new BusinessRuleException("The selected trip has no purchase record yet. Record the pickup under Purchases first.");
        }
        return trip;
    }

    /**
     * Rule: cumulative Total Delivered for a trip must never exceed the trip's
     * Loaded Weight by more than 5 kg. Checked against the CUMULATIVE total for
     * the trip (all deliveries already linked to it, plus this one) - not each
     * delivery individually. excludeDeliveryId excludes the delivery being
     * edited from its own "already delivered" total.
     */
    private void checkDeliveryTolerance(Trip trip, BigDecimal newDispatchWeight, Long excludeDeliveryId) {
        if (trip == null) return;
        BigDecimal loaded = tripService.effectiveLoadedWeight(trip);
        if (loaded == null) return; // Loaded weight unknown for this trip - nothing to validate against.

        BigDecimal alreadyDelivered = deliveryRepository.findByTripId(trip.getId()).stream()
                .filter(existing -> excludeDeliveryId == null || !excludeDeliveryId.equals(existing.getId()))
                .map(Delivery::getDispatchWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal newTotal = alreadyDelivered.add(newDispatchWeight);
        BigDecimal maxAllowed = loaded.add(DELIVERY_TOLERANCE_KG);

        if (newTotal.compareTo(maxAllowed) > 0) {
            throw new BusinessRuleException(String.format(
                    "This delivery would bring Trip %s's total delivered weight to %s kg, which exceeds the loaded weight of %s kg by more than the 5 kg tolerance.",
                    trip.getTripNumber() != null ? trip.getTripNumber() : trip.getId(), newTotal, loaded));
        }
    }

    /**
     * Bird-count rule for tracked trips. Purchased birds are the authoritative
     * loaded count. Deliveries may be entered over multiple stops, and their
     * cumulative bird count may be less than or equal to the purchased count,
     * but it can never exceed it. Save & Finish does not require an exact match.
     * If the purchased bird count is unknown, no bird-count comparison is made.
     */
    private void checkBirdCount(Trip trip, Integer newBirds, Long excludeDeliveryId) {
        if (trip == null) return;

        Long purchasedBirds = tripService.effectiveLoadedBirds(trip);
        if (purchasedBirds == null) return;

        if (newBirds == null) {
            throw new BusinessRuleException(
                    "Number of birds is required for deliveries on this trip because the purchased bird count is available.");
        }

        long alreadyDelivered = deliveryRepository.findByTripId(trip.getId()).stream()
                .filter(existing -> excludeDeliveryId == null || !excludeDeliveryId.equals(existing.getId()))
                .map(Delivery::getNumberOfBirds)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();

        long newTotal = alreadyDelivered + newBirds.longValue();

        // Delivered birds may be LESS THAN or EQUAL TO purchased birds,
        // but must never exceed the purchased count.
        if (newTotal > purchasedBirds) {
            throw new BusinessRuleException(String.format(
                    "This delivery would bring Trip %s's total delivered birds to %d, exceeding the purchased %d birds.",
                    trip.getTripNumber() != null ? trip.getTripNumber() : trip.getId(),
                    newTotal,
                    purchasedBirds));
        }
    }

    public List<DeliveryResponse> findAll() {
        return deliveryRepository.findAll().stream().map(this::toResponse).toList();
    }

    public List<DeliveryResponse> findByCustomer(Long customerId) {
        return deliveryRepository.findByCustomerIdOrderByDeliveryDateDesc(customerId).stream().map(this::toResponse).toList();
    }

    public List<DeliveryResponse> findByDateRange(LocalDate from, LocalDate to) {
        return deliveryRepository.findByDeliveryDateBetweenOrderByDeliveryDateAsc(from, to).stream().map(this::toResponse).toList();
    }

    public List<DeliveryResponse> findByDate(LocalDate date) {
        return deliveryRepository.findByDeliveryDate(date).stream().map(this::toResponse).toList();
    }

    public DeliveryResponse findById(Long id) {
        return toResponse(deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found: " + id)));
    }

    /** Permanently removes a delivery, its linked payment(s), ledger rows and order status impact. */
    @Transactional
    public void delete(Long id) {
        Delivery d = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found: " + id));
        CustomerOrder order = d.getOrder();

        // Payment is a child transaction of this delivery. Delete it first so a
        // deleted sale can never leave cash/payment history behind.
        customerPaymentService.deleteForDelivery(id);
        ledgerService.deleteCustomerLedgerEntryAndRecalculate(d.getCustomer(), LedgerReferenceType.DELIVERY, id);
        deliveryRepository.delete(d);
        deliveryRepository.flush();

        if (order != null) {
            restoreOrderIfNoOtherDelivery(order);
        }
    }

    public DeliveryResponse toResponse(Delivery d) {
        var builder = DeliveryResponse.builder()
                .id(d.getId())
                .customerId(d.getCustomer().getId())
                .customerName(d.getCustomer().getChickenCenterName())
                .orderId(d.getOrder() != null ? d.getOrder().getId() : null)
                .deliveryDate(d.getDeliveryDate())
                .numberOfBoxes(d.getNumberOfBoxes())
                .numberOfBirds(d.getNumberOfBirds())
                .dispatchWeight(d.getDispatchWeight())
                .receivedWeight(d.getReceivedWeight())
                .weightDifference(d.getWeightDifference())
                .sellingRate(d.getSellingRate())
                .salesAmount(d.getSalesAmount())
                .notes(d.getNotes())
                .createdBy(d.getCreatedBy())
                .createdDate(d.getCreatedDate());

        if (d.getTrip() != null) {
            builder.tripId(d.getTrip().getId())
                    .tripNumber(d.getTrip().getTripNumber())
                    .startTime(d.getTrip().getStartTime())
                    .vehicleNumber(d.getTrip().getVehicleNumber())
                    .companyName(d.getTrip().getCompanyName())
                    .driverName(d.getTrip().getDriverName())
                    .helperName(d.getTrip().getHelperName())
                    .totalWeightDispatched(d.getTrip().getTotalWeightDispatched())
                    .distanceKm(d.getTrip().getDistanceKm())
                    .mileage(d.getTrip().getMileage());
        }

        return builder.build();
    }
}
