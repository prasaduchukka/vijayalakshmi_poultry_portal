package com.vpf.service;

import com.vpf.dto.LedgerEntryResponse;
import com.vpf.entity.*;
import com.vpf.entity.enums.LedgerReferenceType;
import com.vpf.repository.CustomerLedgerEntryRepository;
import com.vpf.repository.SupplierLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Central place that keeps customer/supplier running balances consistent.
 * Every delivery, purchase, or payment must go through here so the ledger
 * table always matches the sum of what actually happened - never overwritten,
 * only appended to, exactly like the paper ledger it replaces.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final CustomerLedgerEntryRepository customerLedgerEntryRepository;
    private final SupplierLedgerEntryRepository supplierLedgerEntryRepository;

    // ---------- Customer side ----------

    public BigDecimal getCurrentCustomerBalance(Customer customer) {
        return customerLedgerEntryRepository.findByCustomerIdOrderByEntryDateAscIdAsc(customer.getId()).stream()
                .reduce((a, b) -> b)
                .map(CustomerLedgerEntry::getBalanceAfter)
                .orElse(customer.getOpeningBalance());
    }

    /** Call once when a customer is created, to seed the ledger with their opening balance. */
    public void recordCustomerOpeningBalance(Customer customer) {
        if (customer.getOpeningBalance() == null || customer.getOpeningBalance().compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        CustomerLedgerEntry entry = new CustomerLedgerEntry();
        entry.setCustomer(customer);
        entry.setEntryDate(LocalDate.now());
        entry.setReferenceType(LedgerReferenceType.OPENING);
        entry.setDebit(customer.getOpeningBalance());
        entry.setCredit(BigDecimal.ZERO);
        entry.setBalanceAfter(customer.getOpeningBalance());
        entry.setDescription("Opening balance");
        customerLedgerEntryRepository.save(entry);
        recalculateCustomerLedger(customer.getId());
    }

    /**
     * Updates only the customer's opening-balance ledger row and recalculates running
     * balances. Existing deliveries, payments, feed sales and their references remain untouched.
     */
    @org.springframework.transaction.annotation.Transactional
    public void updateCustomerOpeningBalance(Customer customer, BigDecimal newOpeningBalance) {
        BigDecimal amount = newOpeningBalance == null ? BigDecimal.ZERO : newOpeningBalance;
        List<CustomerLedgerEntry> entries = customerLedgerEntryRepository
                .findByCustomerIdOrderByEntryDateAscIdAsc(customer.getId());
        List<CustomerLedgerEntry> openingEntries = entries.stream()
                .filter(e -> e.getReferenceType() == LedgerReferenceType.OPENING)
                .toList();
        CustomerLedgerEntry opening = openingEntries.isEmpty() ? null : openingEntries.get(0);

        LocalDate earliestTransactionDate = entries.stream()
                .filter(e -> e.getReferenceType() != LedgerReferenceType.OPENING)
                .map(CustomerLedgerEntry::getEntryDate)
                .min(LocalDate::compareTo).orElse(null);

        if (opening == null && amount.compareTo(BigDecimal.ZERO) != 0) {
            opening = new CustomerLedgerEntry();
            opening.setCustomer(customer);
            opening.setReferenceType(LedgerReferenceType.OPENING);
            opening.setReferenceId(null);
            opening.setEntryDate(earliestTransactionDate == null ? LocalDate.now() : earliestTransactionDate.minusDays(1));
            opening.setDescription("Opening balance");
            opening.setCredit(BigDecimal.ZERO);
            opening.setBalanceAfter(BigDecimal.ZERO);
        }

        if (opening != null) {
            // Keep the same ledger row/id where possible; only change the opening amount.
            if (earliestTransactionDate != null && !opening.getEntryDate().isBefore(earliestTransactionDate)) {
                opening.setEntryDate(earliestTransactionDate.minusDays(1));
            }
            opening.setDebit(amount);
            opening.setCredit(BigDecimal.ZERO);
            opening.setDescription("Opening balance");
            customerLedgerEntryRepository.save(opening);
        }

        // Recalculate every snapshot in date/id order without modifying transaction rows.
        recalculateCustomerLedger(customer.getId());
    }

    /** A delivery/sale increases what the customer owes (debit). */
    public void recordCustomerDebit(Customer customer, LocalDate date, LedgerReferenceType type,
                                     Long referenceId, BigDecimal amount, String description) {
        BigDecimal newBalance = getCurrentCustomerBalance(customer).add(amount);
        CustomerLedgerEntry entry = new CustomerLedgerEntry();
        entry.setCustomer(customer);
        entry.setEntryDate(date);
        entry.setReferenceType(type);
        entry.setReferenceId(referenceId);
        entry.setDebit(amount);
        entry.setCredit(BigDecimal.ZERO);
        entry.setBalanceAfter(newBalance);
        entry.setDescription(description);
        customerLedgerEntryRepository.save(entry);
    }

    /** A payment reduces what the customer owes (credit). */
    public void recordCustomerCredit(Customer customer, LocalDate date, LedgerReferenceType type,
                                      Long referenceId, BigDecimal amount, String description) {
        BigDecimal newBalance = getCurrentCustomerBalance(customer).subtract(amount);
        CustomerLedgerEntry entry = new CustomerLedgerEntry();
        entry.setCustomer(customer);
        entry.setEntryDate(date);
        entry.setReferenceType(type);
        entry.setReferenceId(referenceId);
        entry.setDebit(BigDecimal.ZERO);
        entry.setCredit(amount);
        entry.setBalanceAfter(newBalance);
        entry.setDescription(description);
        customerLedgerEntryRepository.save(entry);
        recalculateCustomerLedger(customer.getId());
    }

    public List<LedgerEntryResponse> getCustomerLedger(Long customerId) {
        return customerLedgerEntryRepository.findByCustomerIdOrderByEntryDateAscIdAsc(customerId).stream()
                .map(e -> LedgerEntryResponse.builder()
                        .id(e.getId())
                        .entryDate(e.getEntryDate())
                        .referenceType(e.getReferenceType())
                        .referenceId(e.getReferenceId())
                        .debit(e.getDebit())
                        .credit(e.getCredit())
                        .balanceAfter(e.getBalanceAfter())
                        .description(e.getDescription())
                        .build())
                .toList();
    }

    /**
     * Removes the ledger entry tied to a deleted delivery/payment/feed-sale, then
     * recomputes every later entry's running balance so the ledger stays consistent.
     * Used by admin-only permanent deletes.
     */
    public void deleteCustomerLedgerEntryAndRecalculate(Customer customer, LedgerReferenceType type, Long referenceId) {
        List<CustomerLedgerEntry> all = customerLedgerEntryRepository.findByCustomerIdOrderByEntryDateAscIdAsc(customer.getId());
        List<CustomerLedgerEntry> kept = all.stream()
                .filter(e -> !(e.getReferenceType() == type && referenceId.equals(e.getReferenceId())))
                .toList();
        BigDecimal running = BigDecimal.ZERO;
        for (CustomerLedgerEntry e : kept) {
            running = running.add(e.getDebit()).subtract(e.getCredit());
            e.setBalanceAfter(running);
        }
        customerLedgerEntryRepository.saveAll(kept);
        List<CustomerLedgerEntry> toDelete = all.stream()
                .filter(e -> e.getReferenceType() == type && referenceId.equals(e.getReferenceId()))
                .toList();
        customerLedgerEntryRepository.deleteAll(toDelete);
    }

    // ---------- Supplier side ----------

    public BigDecimal getCurrentSupplierBalance(Supplier supplier) {
        return supplierLedgerEntryRepository.findBySupplierIdOrderByEntryDateAscIdAsc(supplier.getId()).stream()
                .reduce((a, b) -> b)
                .map(SupplierLedgerEntry::getBalanceAfter)
                .orElse(supplier.getOpeningPayableBalance());
    }

    public void recordSupplierOpeningBalance(Supplier supplier) {
        if (supplier.getOpeningPayableBalance() == null
                || supplier.getOpeningPayableBalance().compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        SupplierLedgerEntry entry = new SupplierLedgerEntry();
        entry.setSupplier(supplier);
        entry.setEntryDate(LocalDate.now());
        entry.setReferenceType(LedgerReferenceType.OPENING);
        entry.setDebit(supplier.getOpeningPayableBalance());
        entry.setCredit(BigDecimal.ZERO);
        entry.setBalanceAfter(supplier.getOpeningPayableBalance());
        entry.setDescription("Opening payable balance");
        supplierLedgerEntryRepository.save(entry);
        recalculateSupplierLedger(supplier.getId());
    }

    /** A purchase increases what we owe the supplier (debit/payable). */
    public void recordSupplierDebit(Supplier supplier, LocalDate date, LedgerReferenceType type,
                                     Long referenceId, BigDecimal amount, String description) {
        BigDecimal newBalance = getCurrentSupplierBalance(supplier).add(amount);
        SupplierLedgerEntry entry = new SupplierLedgerEntry();
        entry.setSupplier(supplier);
        entry.setEntryDate(date);
        entry.setReferenceType(type);
        entry.setReferenceId(referenceId);
        entry.setDebit(amount);
        entry.setCredit(BigDecimal.ZERO);
        entry.setBalanceAfter(newBalance);
        entry.setDescription(description);
        supplierLedgerEntryRepository.save(entry);
        recalculateSupplierLedger(supplier.getId());
    }

    /** A payment to the supplier reduces what we owe them (credit). */
    public void recordSupplierCredit(Supplier supplier, LocalDate date, LedgerReferenceType type,
                                      Long referenceId, BigDecimal amount, String description) {
        BigDecimal newBalance = getCurrentSupplierBalance(supplier).subtract(amount);
        SupplierLedgerEntry entry = new SupplierLedgerEntry();
        entry.setSupplier(supplier);
        entry.setEntryDate(date);
        entry.setReferenceType(type);
        entry.setReferenceId(referenceId);
        entry.setDebit(BigDecimal.ZERO);
        entry.setCredit(amount);
        entry.setBalanceAfter(newBalance);
        entry.setDescription(description);
        supplierLedgerEntryRepository.save(entry);
        recalculateSupplierLedger(supplier.getId());
    }

    public List<LedgerEntryResponse> getSupplierLedger(Long supplierId) {
        return supplierLedgerEntryRepository.findBySupplierIdOrderByEntryDateAscIdAsc(supplierId).stream()
                .map(e -> LedgerEntryResponse.builder()
                        .id(e.getId())
                        .entryDate(e.getEntryDate())
                        .referenceType(e.getReferenceType())
                        .referenceId(e.getReferenceId())
                        .debit(e.getDebit())
                        .credit(e.getCredit())
                        .balanceAfter(e.getBalanceAfter())
                        .description(e.getDescription())
                        .build())
                .toList();
    }

    /** Supplier equivalent of deleteCustomerLedgerEntryAndRecalculate - see that method for details. */
    public void deleteSupplierLedgerEntryAndRecalculate(Supplier supplier, LedgerReferenceType type, Long referenceId) {
        List<SupplierLedgerEntry> all = supplierLedgerEntryRepository.findBySupplierIdOrderByEntryDateAscIdAsc(supplier.getId());
        List<SupplierLedgerEntry> kept = all.stream()
                .filter(e -> !(e.getReferenceType() == type && referenceId.equals(e.getReferenceId())))
                .toList();
        BigDecimal running = BigDecimal.ZERO;
        for (SupplierLedgerEntry e : kept) {
            running = running.add(e.getDebit()).subtract(e.getCredit());
            e.setBalanceAfter(running);
        }
        supplierLedgerEntryRepository.saveAll(kept);
        List<SupplierLedgerEntry> toDelete = all.stream()
                .filter(e -> e.getReferenceType() == type && referenceId.equals(e.getReferenceId()))
                .toList();
        supplierLedgerEntryRepository.deleteAll(toDelete);
    }

    @org.springframework.transaction.annotation.Transactional
    private void recalculateCustomerLedger(Long customerId) {
        List<CustomerLedgerEntry> entries = customerLedgerEntryRepository.findByCustomerIdOrderByEntryDateAscIdAsc(customerId);
        BigDecimal running = BigDecimal.ZERO;
        for (CustomerLedgerEntry e : entries) {
            running = running.add(e.getDebit() == null ? BigDecimal.ZERO : e.getDebit())
                    .subtract(e.getCredit() == null ? BigDecimal.ZERO : e.getCredit());
            e.setBalanceAfter(running);
        }
        customerLedgerEntryRepository.saveAll(entries);
    }

    @org.springframework.transaction.annotation.Transactional
    private void recalculateSupplierLedger(Long supplierId) {
        List<SupplierLedgerEntry> entries = supplierLedgerEntryRepository.findBySupplierIdOrderByEntryDateAscIdAsc(supplierId);
        BigDecimal running = BigDecimal.ZERO;
        for (SupplierLedgerEntry e : entries) {
            running = running.add(e.getDebit() == null ? BigDecimal.ZERO : e.getDebit())
                    .subtract(e.getCredit() == null ? BigDecimal.ZERO : e.getCredit());
            e.setBalanceAfter(running);
        }
        supplierLedgerEntryRepository.saveAll(entries);
    }

    /**
     * Used by admin edits: replaces the ledger entry for (type, referenceId) with new
     * values, then recalculates every entry's running balance in true chronological
     * order. This is what makes editing a past delivery/payment/purchase safe -
     * everything downstream of it re-balances automatically.
     */
    @org.springframework.transaction.annotation.Transactional
    public void replaceCustomerEntry(Customer customer, LedgerReferenceType type, Long referenceId,
                                      LocalDate newDate, BigDecimal newDebit, BigDecimal newCredit, String newDescription) {
        List<CustomerLedgerEntry> existing = customerLedgerEntryRepository.findByCustomerIdOrderByEntryDateAscIdAsc(customer.getId());
        List<CustomerLedgerEntry> toDelete = existing.stream()
                .filter(e -> e.getReferenceType() == type && referenceId.equals(e.getReferenceId()))
                .toList();
        customerLedgerEntryRepository.deleteAll(toDelete);

        CustomerLedgerEntry entry = new CustomerLedgerEntry();
        entry.setCustomer(customer);
        entry.setEntryDate(newDate);
        entry.setReferenceType(type);
        entry.setReferenceId(referenceId);
        entry.setDebit(newDebit == null ? BigDecimal.ZERO : newDebit);
        entry.setCredit(newCredit == null ? BigDecimal.ZERO : newCredit);
        entry.setBalanceAfter(BigDecimal.ZERO); // placeholder - fixed by the recalculation below
        entry.setDescription(newDescription);
        customerLedgerEntryRepository.save(entry);

        List<CustomerLedgerEntry> refreshed = customerLedgerEntryRepository.findByCustomerIdOrderByEntryDateAscIdAsc(customer.getId());
        BigDecimal running = BigDecimal.ZERO;
        for (CustomerLedgerEntry e : refreshed) {
            running = running.add(e.getDebit()).subtract(e.getCredit());
            e.setBalanceAfter(running);
        }
        customerLedgerEntryRepository.saveAll(refreshed);
    }

    /** Supplier equivalent of replaceCustomerEntry - see that method for details. */
    @org.springframework.transaction.annotation.Transactional
    public void replaceSupplierEntry(Supplier supplier, LedgerReferenceType type, Long referenceId,
                                      LocalDate newDate, BigDecimal newDebit, BigDecimal newCredit, String newDescription) {
        List<SupplierLedgerEntry> existing = supplierLedgerEntryRepository.findBySupplierIdOrderByEntryDateAscIdAsc(supplier.getId());
        List<SupplierLedgerEntry> toDelete = existing.stream()
                .filter(e -> e.getReferenceType() == type && referenceId.equals(e.getReferenceId()))
                .toList();
        supplierLedgerEntryRepository.deleteAll(toDelete);

        SupplierLedgerEntry entry = new SupplierLedgerEntry();
        entry.setSupplier(supplier);
        entry.setEntryDate(newDate);
        entry.setReferenceType(type);
        entry.setReferenceId(referenceId);
        entry.setDebit(newDebit == null ? BigDecimal.ZERO : newDebit);
        entry.setCredit(newCredit == null ? BigDecimal.ZERO : newCredit);
        entry.setBalanceAfter(BigDecimal.ZERO);
        entry.setDescription(newDescription);
        supplierLedgerEntryRepository.save(entry);

        List<SupplierLedgerEntry> refreshed = supplierLedgerEntryRepository.findBySupplierIdOrderByEntryDateAscIdAsc(supplier.getId());
        BigDecimal running = BigDecimal.ZERO;
        for (SupplierLedgerEntry e : refreshed) {
            running = running.add(e.getDebit()).subtract(e.getCredit());
            e.setBalanceAfter(running);
        }
        supplierLedgerEntryRepository.saveAll(refreshed);
    }
}
