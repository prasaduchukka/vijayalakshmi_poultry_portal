package com.vpf.service;

import com.vpf.dto.FeedSaleRequest;
import com.vpf.dto.FeedSaleResponse;
import com.vpf.entity.Customer;
import com.vpf.entity.FeedInventory;
import com.vpf.entity.FeedSale;
import com.vpf.entity.enums.LedgerReferenceType;
import com.vpf.exception.BusinessRuleException;
import com.vpf.repository.FeedSaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedSaleService {
    private final FeedSaleRepository feedSaleRepository;
    private final CustomerService customerService;
    private final LedgerService ledgerService;
    private final FeedInventoryService inventoryService;

    @Transactional
    public FeedSaleResponse create(FeedSaleRequest req) {
        FeedInventory inventory = inventoryService.getLockedInventory();
        inventoryService.ensureAvailable(inventory, req.getBagCount());
        Customer customer = customerService.getOrThrow(req.getCustomerId());

        BigDecimal unitPrice = inventory.getBagPrice();
        BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(req.getBagCount()));
        inventory.setStockBags(inventory.getStockBags() - req.getBagCount());

        FeedSale f = new FeedSale();
        f.setCustomer(customer);
        f.setSaleDate(req.getSaleDate());
        f.setBagCount(req.getBagCount());
        f.setUnitPrice(unitPrice);
        f.setAmount(amount);
        BigDecimal amountPaid = req.getAmountPaid() == null ? BigDecimal.ZERO : req.getAmountPaid();
        validateAmountPaid(amountPaid, amount);
        f.setAmountPaid(amountPaid);
        f.setDescription(req.getDescription() == null || req.getDescription().isBlank()
                ? req.getBagCount() + " feed bag(s)" : req.getDescription());
        f.setNotes(req.getNotes());
        f.setCreatedBy(req.getCreatedBy());
        feedSaleRepository.save(f);

        ledgerService.recordCustomerDebit(customer, req.getSaleDate(), LedgerReferenceType.FEED_SALE,
                f.getId(), amount, "Feed sale - " + f.getBagCount() + " bag(s) @ " + unitPrice);
        if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.recordCustomerCredit(customer, req.getSaleDate(), LedgerReferenceType.FEED_PAYMENT,
                    f.getId(), amountPaid, "Payment received against feed sale #" + f.getId());
        }
        return toResponse(f);
    }

    /** Admin edit: adjusts stock by the difference and recalculates the ledger. */
    @Transactional
    public FeedSaleResponse update(Long id, FeedSaleRequest req) {
        FeedSale f = feedSaleRepository.findById(id)
                .orElseThrow(() -> new com.vpf.exception.ResourceNotFoundException("Feed sale not found: " + id));
        FeedInventory inventory = inventoryService.getLockedInventory();
        int oldBags = f.getBagCount() == null ? 0 : f.getBagCount();
        int delta = req.getBagCount() - oldBags;
        if (delta > 0) inventoryService.ensureAvailable(inventory, delta);
        inventory.setStockBags(inventory.getStockBags() - delta);

        Customer oldCustomer = f.getCustomer();
        Customer newCustomer = customerService.getOrThrow(req.getCustomerId());
        BigDecimal unitPrice = f.getUnitPrice();
        if (unitPrice == null) {
            // Legacy sale without a stored price: keep its original price (stored amount / bags), never today's price.
            unitPrice = (oldBags > 0 && f.getAmount() != null)
                    ? f.getAmount().divide(BigDecimal.valueOf(oldBags), 2, java.math.RoundingMode.HALF_UP)
                    : inventory.getBagPrice();
        }
        BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(req.getBagCount()));
        BigDecimal amountPaid = req.getAmountPaid() == null
                ? (f.getAmountPaid() == null ? BigDecimal.ZERO : f.getAmountPaid()) : req.getAmountPaid();
        validateAmountPaid(amountPaid, amount);

        if (!oldCustomer.getId().equals(newCustomer.getId())) {
            ledgerService.deleteCustomerLedgerEntryAndRecalculate(oldCustomer, LedgerReferenceType.FEED_SALE, id);
            ledgerService.deleteCustomerLedgerEntryAndRecalculate(oldCustomer, LedgerReferenceType.FEED_PAYMENT, id);
            ledgerService.recordCustomerDebit(newCustomer, req.getSaleDate(), LedgerReferenceType.FEED_SALE,
                    id, amount, "Feed sale - " + req.getBagCount() + " bag(s) @ " + unitPrice);
            if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
                ledgerService.recordCustomerCredit(newCustomer, req.getSaleDate(), LedgerReferenceType.FEED_PAYMENT,
                        id, amountPaid, "Payment received against feed sale #" + id);
            }
        } else {
            ledgerService.replaceCustomerEntry(newCustomer, LedgerReferenceType.FEED_SALE, id,
                    req.getSaleDate(), amount, null,
                    "Feed sale - " + req.getBagCount() + " bag(s) @ " + unitPrice);
            if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
                ledgerService.replaceCustomerEntry(newCustomer, LedgerReferenceType.FEED_PAYMENT, id,
                        req.getSaleDate(), null, amountPaid, "Payment received against feed sale #" + id);
            } else {
                ledgerService.deleteCustomerLedgerEntryAndRecalculate(newCustomer, LedgerReferenceType.FEED_PAYMENT, id);
            }
        }
        f.setCustomer(newCustomer);
        f.setSaleDate(req.getSaleDate());
        f.setBagCount(req.getBagCount());
        f.setUnitPrice(unitPrice);
        f.setAmount(amount);
        f.setAmountPaid(amountPaid);
        f.setDescription(req.getDescription());
        f.setNotes(req.getNotes());
        return toResponse(feedSaleRepository.save(f));
    }

    public List<FeedSaleResponse> findByCustomer(Long customerId) {
        return feedSaleRepository.findByCustomerIdOrderBySaleDateDesc(customerId).stream().map(this::toResponse).toList();
    }

    public List<FeedSaleResponse> findAll() {
        return feedSaleRepository.findAll().stream()
                .sorted((a,b) -> b.getSaleDate().compareTo(a.getSaleDate()))
                .map(this::toResponse).toList();
    }

    @Transactional
    public void delete(Long id) {
        FeedSale f = feedSaleRepository.findById(id)
                .orElseThrow(() -> new com.vpf.exception.ResourceNotFoundException("Feed sale not found: " + id));
        FeedInventory inventory = inventoryService.getLockedInventory();
        inventory.setStockBags(Math.addExact(inventory.getStockBags(), f.getBagCount() == null ? 0 : f.getBagCount()));
        ledgerService.deleteCustomerLedgerEntryAndRecalculate(f.getCustomer(), LedgerReferenceType.FEED_SALE, id);
        ledgerService.deleteCustomerLedgerEntryAndRecalculate(f.getCustomer(), LedgerReferenceType.FEED_PAYMENT, id);
        feedSaleRepository.delete(f);
    }

    private void validateAmountPaid(BigDecimal paid, BigDecimal total) {
        if (paid.compareTo(BigDecimal.ZERO) < 0) throw new BusinessRuleException("Amount paid cannot be negative.");
        if (paid.compareTo(total) > 0) throw new BusinessRuleException("Amount paid cannot exceed the total feed sale cost.");
    }

    private FeedSaleResponse toResponse(FeedSale f) {
        return FeedSaleResponse.builder()
                .id(f.getId()).customerId(f.getCustomer().getId())
                .customerName(f.getCustomer().getChickenCenterName())
                .saleDate(f.getSaleDate()).bagCount(f.getBagCount()).unitPrice(f.getUnitPrice())
                .amount(f.getAmount()).amountPaid(f.getAmountPaid() == null ? BigDecimal.ZERO : f.getAmountPaid())
                .balanceDue(f.getAmount().subtract(f.getAmountPaid() == null ? BigDecimal.ZERO : f.getAmountPaid()))
                .description(f.getDescription()).notes(f.getNotes())
                .createdBy(f.getCreatedBy()).createdDate(f.getCreatedDate()).build();
    }
}
