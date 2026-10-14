package com.vpf.service;

import com.vpf.dto.SupplierPaymentRequest;
import com.vpf.dto.SupplierPaymentResponse;
import com.vpf.entity.Supplier;
import com.vpf.entity.SupplierPayment;
import com.vpf.entity.enums.LedgerReferenceType;
import com.vpf.repository.SupplierPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierPaymentService {

    private final SupplierPaymentRepository supplierPaymentRepository;
    private final SupplierService supplierService;
    private final LedgerService ledgerService;

    @Transactional
    public SupplierPaymentResponse create(SupplierPaymentRequest req) {
        Supplier supplier = supplierService.getOrThrow(req.getSupplierId());

        SupplierPayment payment = new SupplierPayment();
        payment.setSupplier(supplier);
        payment.setPaymentDate(req.getPaymentDate());
        payment.setAmount(req.getAmount());
        payment.setPaymentMethod(req.getPaymentMethod());
        payment.setReferenceNumber(req.getReferenceNumber());
        payment.setNotes(req.getNotes());
        payment.setCreatedBy(req.getCreatedBy());
        supplierPaymentRepository.save(payment);

        ledgerService.recordSupplierCredit(supplier, req.getPaymentDate(), LedgerReferenceType.PAYMENT,
                payment.getId(), req.getAmount(),
                "Payment made (" + req.getPaymentMethod() + ")" + (req.getReferenceNumber() != null ? " Ref: " + req.getReferenceNumber() : ""));

        return toResponse(payment);
    }

    /** Admin-only edit. Recalculates the supplier's ledger from this entry forward. */
    @Transactional
    public SupplierPaymentResponse update(Long id, SupplierPaymentRequest req) {
        SupplierPayment payment = supplierPaymentRepository.findById(id)
                .orElseThrow(() -> new com.vpf.exception.ResourceNotFoundException("Payment not found: " + id));
        Supplier supplier = supplierService.getOrThrow(req.getSupplierId());
        payment.setSupplier(supplier);
        payment.setPaymentDate(req.getPaymentDate());
        payment.setAmount(req.getAmount());
        payment.setPaymentMethod(req.getPaymentMethod());
        payment.setReferenceNumber(req.getReferenceNumber());
        payment.setNotes(req.getNotes());
        supplierPaymentRepository.save(payment);

        ledgerService.replaceSupplierEntry(supplier, LedgerReferenceType.PAYMENT, payment.getId(),
                req.getPaymentDate(), null, req.getAmount(),
                "Payment made (" + req.getPaymentMethod() + ")" + (req.getReferenceNumber() != null ? " Ref: " + req.getReferenceNumber() : ""));

        return toResponse(payment);
    }

    public List<SupplierPaymentResponse> findBySupplier(Long supplierId) {
        return supplierPaymentRepository.findBySupplierIdOrderByPaymentDateDesc(supplierId).stream().map(this::toResponse).toList();
    }

    /** Permanently removes a payment and reverses its effect on the supplier's ledger. Admin-only. */
    @Transactional
    public void delete(Long id) {
        SupplierPayment p = supplierPaymentRepository.findById(id)
                .orElseThrow(() -> new com.vpf.exception.ResourceNotFoundException("Payment not found: " + id));
        ledgerService.deleteSupplierLedgerEntryAndRecalculate(p.getSupplier(), LedgerReferenceType.PAYMENT, id);
        supplierPaymentRepository.delete(p);
    }

    public SupplierPaymentResponse toResponse(SupplierPayment p) {
        return SupplierPaymentResponse.builder()
                .id(p.getId())
                .supplierId(p.getSupplier().getId())
                .supplierName(p.getSupplier().getSupplierName())
                .paymentDate(p.getPaymentDate())
                .amount(p.getAmount())
                .paymentMethod(p.getPaymentMethod())
                .referenceNumber(p.getReferenceNumber())
                .notes(p.getNotes())
                .createdBy(p.getCreatedBy())
                .createdDate(p.getCreatedDate())
                .build();
    }
}
