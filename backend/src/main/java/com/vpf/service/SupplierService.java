package com.vpf.service;

import com.vpf.dto.*;
import com.vpf.entity.Supplier;
import com.vpf.exception.ResourceNotFoundException;
import com.vpf.repository.PurchaseRepository;
import com.vpf.repository.SupplierLedgerEntryRepository;
import com.vpf.repository.SupplierPaymentRepository;
import com.vpf.repository.SupplierRepository;
import com.vpf.entity.Purchase;
import com.vpf.entity.Trip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final PurchaseRepository purchaseRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final SupplierLedgerEntryRepository supplierLedgerEntryRepository;
    private final LedgerService ledgerService;
    private final TripService tripService;

    @Transactional
    public SupplierResponse create(SupplierRequest req) {
        Supplier s = new Supplier();
        apply(s, req);
        supplierRepository.save(s);
        ledgerService.recordSupplierOpeningBalance(s);
        return toResponse(s);
    }

    public SupplierResponse update(Long id, SupplierRequest req) {
        Supplier s = getOrThrow(id);
        apply(s, req);
        supplierRepository.save(s);
        return toResponse(s);
    }

    /** Permanently deletes a supplier and every purchase/payment/ledger entry tied to them. Admin-only. */
    @Transactional
    public void delete(Long id) {
        Supplier s = getOrThrow(id);
        List<Purchase> purchases = purchaseRepository.findBySupplierIdOrderByPurchaseDateDesc(id);
        java.util.Set<Long> affectedTripIds = purchases.stream()
                .map(Purchase::getTrip)
                .filter(java.util.Objects::nonNull)
                .map(Trip::getId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        // A shared trip with deliveries cannot be safely split after removing one
        // supplier: the existing delivery weights do not record which supplier
        // contributed each kilogram/bird. Refuse the destructive operation instead
        // of silently creating a wrong vehicle/customer history.
        for (Long tripId : affectedTripIds) {
            List<Purchase> tripPurchases = purchaseRepository.findByTripId(tripId);
            boolean hasOtherSupplierPurchase = tripPurchases.stream().anyMatch(p -> !p.getSupplier().getId().equals(id));
            if (hasOtherSupplierPurchase && !tripService.deliveriesForTrip(tripId).isEmpty()) {
                throw new com.vpf.exception.BusinessRuleException(
                        "Supplier cannot be deleted yet because one of its purchases shares Trip "
                        + tripService.getOrThrow(tripId).getTripNumber()
                        + " with another supplier and that trip already has deliveries. Delete/review those deliveries first so the remaining trip history stays accurate.");
            }
        }

        // Supplier-side money/history must disappear together.
        supplierPaymentRepository.deleteAll(supplierPaymentRepository.findBySupplierIdOrderByPaymentDateDesc(id));
        purchaseRepository.deleteAll(purchases);
        purchaseRepository.flush();
        supplierLedgerEntryRepository.deleteAll(supplierLedgerEntryRepository.findBySupplierIdOrderByEntryDateAscIdAsc(id));
        supplierLedgerEntryRepository.flush();

        // A trip belongs to the purchase chain. If this supplier was the last
        // purchase using a trip, remove the entire trip, deliveries, delivery
        // payments and customer ledgers too. If another supplier still uses the
        // same trip, keep it because it is still valid shared business data.
        for (Long tripId : affectedTripIds) {
            if (purchaseRepository.findByTripId(tripId).isEmpty()) {
                tripService.deleteTripAndDeliveries(tripId);
            }
        }

        supplierRepository.delete(s);
    }

    public List<SupplierResponse> findAll() {
        return supplierRepository.findAll().stream().map(this::toResponse).toList();
    }

    public SupplierResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    public Supplier getOrThrow(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + id));
    }

    public SupplierAccountSummary getAccountSummary(Long id) {
        Supplier s = getOrThrow(id);
        BigDecimal totalPurchased = purchaseRepository.sumPurchasesForSupplier(id);
        BigDecimal totalPaid = supplierPaymentRepository.sumPaidForSupplier(id);
        BigDecimal outstanding = ledgerService.getCurrentSupplierBalance(s);
        return SupplierAccountSummary.builder()
                .supplier(toResponse(s))
                .currentOutstandingPayable(outstanding)
                .totalPurchased(totalPurchased)
                .totalPaid(totalPaid)
                .build();
    }

    private void apply(Supplier s, SupplierRequest req) {
        s.setSupplierName(req.getSupplierName());
        s.setContactPerson(req.getContactPerson());
        s.setPhoneNumber(req.getPhoneNumber());
        s.setAddress(req.getAddress());
        if (s.getId() == null) {
            s.setOpeningPayableBalance(req.getOpeningPayableBalance() == null ? BigDecimal.ZERO : req.getOpeningPayableBalance());
        }
        s.setStatus(req.getStatus());
        s.setNotes(req.getNotes());
    }

    private SupplierResponse toResponse(Supplier s) {
        return SupplierResponse.builder()
                .id(s.getId())
                .supplierName(s.getSupplierName())
                .contactPerson(s.getContactPerson())
                .phoneNumber(s.getPhoneNumber())
                .address(s.getAddress())
                .openingPayableBalance(s.getOpeningPayableBalance())
                .status(s.getStatus())
                .notes(s.getNotes())
                .createdDate(s.getCreatedDate())
                .updatedDate(s.getUpdatedDate())
                .build();
    }
}
