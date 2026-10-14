package com.vpf.service;

import com.vpf.dto.FeedSaleRequest;
import com.vpf.dto.FeedSaleResponse;
import com.vpf.entity.Customer;
import com.vpf.entity.FeedSale;
import com.vpf.entity.enums.LedgerReferenceType;
import com.vpf.repository.FeedSaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedSaleService {

    private final FeedSaleRepository feedSaleRepository;
    private final CustomerService customerService;
    private final LedgerService ledgerService;

    @Transactional
    public FeedSaleResponse create(FeedSaleRequest req) {
        Customer customer = customerService.getOrThrow(req.getCustomerId());

        FeedSale f = new FeedSale();
        f.setCustomer(customer);
        f.setSaleDate(req.getSaleDate());
        f.setAmount(req.getAmount());
        f.setDescription(req.getDescription());
        f.setNotes(req.getNotes());
        f.setCreatedBy(req.getCreatedBy());
        feedSaleRepository.save(f);

        // A feed sale increases what the customer owes, same as a delivery.
        ledgerService.recordCustomerDebit(customer, req.getSaleDate(), LedgerReferenceType.FEED_SALE,
                f.getId(), f.getAmount(),
                "Feed sale" + (req.getDescription() != null ? " - " + req.getDescription() : ""));

        return toResponse(f);
    }

    /** Admin-only edit. Recalculates the customer's ledger from this entry forward. */
    @Transactional
    public FeedSaleResponse update(Long id, FeedSaleRequest req) {
        FeedSale f = feedSaleRepository.findById(id)
                .orElseThrow(() -> new com.vpf.exception.ResourceNotFoundException("Feed sale not found: " + id));
        Customer customer = customerService.getOrThrow(req.getCustomerId());
        f.setCustomer(customer);
        f.setSaleDate(req.getSaleDate());
        f.setAmount(req.getAmount());
        f.setDescription(req.getDescription());
        f.setNotes(req.getNotes());
        feedSaleRepository.save(f);

        ledgerService.replaceCustomerEntry(customer, LedgerReferenceType.FEED_SALE, f.getId(),
                req.getSaleDate(), req.getAmount(), null,
                "Feed sale" + (req.getDescription() != null ? " - " + req.getDescription() : ""));

        return toResponse(f);
    }

    public List<FeedSaleResponse> findByCustomer(Long customerId) {
        return feedSaleRepository.findByCustomerIdOrderBySaleDateDesc(customerId).stream().map(this::toResponse).toList();
    }

    /** Permanently removes a feed sale and reverses its effect on the customer's ledger. Admin-only. */
    @Transactional
    public void delete(Long id) {
        FeedSale f = feedSaleRepository.findById(id)
                .orElseThrow(() -> new com.vpf.exception.ResourceNotFoundException("Feed sale not found: " + id));
        ledgerService.deleteCustomerLedgerEntryAndRecalculate(f.getCustomer(), LedgerReferenceType.FEED_SALE, id);
        feedSaleRepository.delete(f);
    }

    private FeedSaleResponse toResponse(FeedSale f) {
        return FeedSaleResponse.builder()
                .id(f.getId())
                .customerId(f.getCustomer().getId())
                .customerName(f.getCustomer().getChickenCenterName())
                .saleDate(f.getSaleDate())
                .amount(f.getAmount())
                .description(f.getDescription())
                .notes(f.getNotes())
                .createdBy(f.getCreatedBy())
                .createdDate(f.getCreatedDate())
                .build();
    }
}
