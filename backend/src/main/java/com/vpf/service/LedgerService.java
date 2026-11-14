package com.vpf.service;

import com.vpf.dto.LedgerEntryResponse;
import com.vpf.entity.*;
import com.vpf.entity.enums.LedgerReferenceType;
import com.vpf.repository.CustomerLedgerEntryRepository;
import com.vpf.repository.CustomerRepository;
import com.vpf.repository.DeliveryRepository;
import com.vpf.repository.CustomerPaymentRepository;
import com.vpf.repository.FeedSaleRepository;
import com.vpf.repository.PurchaseRepository;
import com.vpf.repository.SupplierPaymentRepository;
import com.vpf.repository.SupplierLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;

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
    private final CustomerRepository customerRepository;
    private final DeliveryRepository deliveryRepository;
    private final CustomerPaymentRepository customerPaymentRepository;
    private final FeedSaleRepository feedSaleRepository;
    private final PurchaseRepository purchaseRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;

    // ---------- Customer side ----------

    /**
     * Self-heals every customer's ledger table from the real source records
     * (opening balance, deliveries, feed sales, payments) once at startup, so
     * orphaned rows left behind by older versions/deletes cannot survive.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void repairCustomerLedgerSnapshotsOnStartup() {
        customerRepository.findAll().forEach(c -> reconcileCustomerLedger(c.getId()));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * The customer's outstanding balance, computed from the real records:
     * opening + every delivery (chicken + feed) + standalone feed sales - every payment.
     * This is exactly what the on-screen ledger and the PDF statement show, so the
     * three can never disagree, whatever state the ledger snapshot table is in.
     */
    public BigDecimal getCurrentCustomerBalance(Customer customer) {
        BigDecimal feedSales = feedSaleRepository.findByCustomerIdOrderBySaleDateDesc(customer.getId()).stream()
                .map(f -> nz(f.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return nz(customer.getOpeningBalance())
                .add(nz(deliveryRepository.sumSalesForCustomer(customer.getId())))
                .add(feedSales)
                .subtract(nz(customerPaymentRepository.sumPaidForCustomer(customer.getId())));
    }

    /**
     * Rebuilds the customer's ledger rows from the real records when (and only when)
     * they differ from what is stored: removes orphan rows whose source record no
     * longer exists, adds missing rows, then recalculates every running balance.
     * Business records (deliveries, payments, feed sales) are never modified.
     */
    @Transactional
    public void reconcileCustomerLedger(Long customerId) {
        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) return;

        record Row(LocalDate date, LedgerReferenceType type, Long refId, BigDecimal debit, BigDecimal credit, String desc) {
            String key() {
                return type + "|" + refId + "|" + debit.stripTrailingZeros().toPlainString()
                        + "|" + credit.stripTrailingZeros().toPlainString()
                        + "|" + (type == LedgerReferenceType.OPENING ? "" : String.valueOf(date));
            }
        }

        List<Row> expected = new java.util.ArrayList<>();
        BigDecimal opening = nz(customer.getOpeningBalance());
        if (opening.compareTo(BigDecimal.ZERO) != 0) {
            LocalDate d = customer.getCreatedDate() != null ? customer.getCreatedDate().toLocalDate() : LocalDate.now();
            expected.add(new Row(d, LedgerReferenceType.OPENING, null, opening, BigDecimal.ZERO, "Opening balance"));
        }
        deliveryRepository.findByCustomerIdOrderByDeliveryDateDesc(customerId).stream()
                .sorted(java.util.Comparator.comparing(Delivery::getDeliveryDate).thenComparing(Delivery::getId))
                .forEach(dl -> {
                    expected.add(new Row(dl.getDeliveryDate(), LedgerReferenceType.DELIVERY, dl.getId(),
                            nz(dl.getSalesAmount()), BigDecimal.ZERO,
                            "Delivery #" + dl.getId() + " @ Rs." + dl.getSellingRate() + "/kg"));
                });
        feedSaleRepository.findByCustomerIdOrderBySaleDateDesc(customerId).stream()
                .sorted(java.util.Comparator.comparing(FeedSale::getSaleDate).thenComparing(FeedSale::getId))
                .forEach(f -> expected.add(new Row(f.getSaleDate(), LedgerReferenceType.FEED_SALE, f.getId(),
                        nz(f.getAmount()), BigDecimal.ZERO,
                        "Feed sale" + (f.getDescription() != null ? " - " + f.getDescription() : ""))));
        customerPaymentRepository.findByCustomerIdOrderByPaymentDateDesc(customerId).stream()
                .sorted(java.util.Comparator.comparing(CustomerPayment::getPaymentDate).thenComparing(CustomerPayment::getId))
                .forEach(p -> expected.add(new Row(p.getPaymentDate(), LedgerReferenceType.PAYMENT, p.getId(),
                        BigDecimal.ZERO, nz(p.getAmount()),
                        p.getReferenceNumber() != null && !p.getReferenceNumber().isBlank()
                                ? "Payment received - " + p.getReferenceNumber()
                                : (p.getDelivery() != null
                                    ? "Payment received for Delivery #" + p.getDelivery().getId() + " (" + p.getPaymentMethod() + ")"
                                    : "Payment received (" + p.getPaymentMethod() + ")"))));

        List<CustomerLedgerEntry> stored = customerLedgerEntryRepository.findByCustomerIdOrderByEntryDateAscIdAsc(customerId);
        java.util.List<String> expectedKeys = expected.stream().map(Row::key).sorted().toList();
        java.util.List<String> storedKeys = stored.stream()
                .map(e -> new Row(e.getEntryDate(), e.getReferenceType(), e.getReferenceId(),
                        nz(e.getDebit()), nz(e.getCredit()), null).key())
                .sorted().toList();

        if (!expectedKeys.equals(storedKeys)) {
            customerLedgerEntryRepository.deleteAll(stored);
            customerLedgerEntryRepository.flush();
            // Opening balance first, then chronological, so same-day rows keep a sensible order.
            LocalDate earliest = expected.stream().map(Row::date).min(LocalDate::compareTo).orElse(null);
            for (Row r : expected) {
                CustomerLedgerEntry e = new CustomerLedgerEntry();
                e.setCustomer(customer);
                e.setEntryDate(r.type() == LedgerReferenceType.OPENING && earliest != null && earliest.isBefore(r.date())
                        ? earliest : r.date());
                e.setReferenceType(r.type());
                e.setReferenceId(r.refId());
                e.setDebit(r.debit());
                e.setCredit(r.credit());
                e.setBalanceAfter(BigDecimal.ZERO);
                e.setDescription(r.desc());
                customerLedgerEntryRepository.save(e);
            }
            customerLedgerEntryRepository.flush();
        }
        recalculateCustomerLedger(customerId);
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
     * Updates the customer's opening balance ledger entry.
     *
     * The opening balance is treated as the customer's initial ledger position,
     * not as a new payment/debit transaction. When the opening balance changes,
     * the existing OPENING entry is replaced/created/removed and all later
     * running balances are recalculated.
     */
    @org.springframework.transaction.annotation.Transactional
    public void updateCustomerOpeningBalance(Customer customer, BigDecimal newOpeningBalance) {

        BigDecimal newBalance =
                newOpeningBalance == null ? BigDecimal.ZERO : newOpeningBalance;

        List<CustomerLedgerEntry> entries =
                customerLedgerEntryRepository
                        .findByCustomerIdOrderByEntryDateAscIdAsc(customer.getId());

        List<CustomerLedgerEntry> openingEntries = entries.stream()
                .filter(e -> e.getReferenceType() == LedgerReferenceType.OPENING)
                .toList();

        // Remove any existing OPENING entries first.
        if (!openingEntries.isEmpty()) {
            customerLedgerEntryRepository.deleteAll(openingEntries);
            customerLedgerEntryRepository.flush();
        }

        // If the new opening balance is zero, there is no need for
        // an OPENING ledger row.
        if (newBalance.compareTo(BigDecimal.ZERO) == 0) {
            recalculateCustomerLedger(customer.getId());
            return;
        }

        CustomerLedgerEntry entry = new CustomerLedgerEntry();
        entry.setCustomer(customer);

        /*
        * Keep the original customer creation date for a newly-created
        * opening balance when possible. This keeps the opening balance
        * at the beginning of the customer's account history.
        */
        LocalDate openingDate = customer.getCreatedDate() != null
                ? customer.getCreatedDate().toLocalDate()
                : LocalDate.now();

        entry.setEntryDate(openingDate);
        entry.setReferenceType(LedgerReferenceType.OPENING);

        // OPENING entries do not belong to a delivery/payment.
        entry.setReferenceId(null);

        entry.setDebit(newBalance);
        entry.setCredit(BigDecimal.ZERO);
        entry.setBalanceAfter(BigDecimal.ZERO);
        entry.setDescription("Opening balance");

        customerLedgerEntryRepository.save(entry);

        // Recalculate every customer ledger row so all balanceAfter
        // values reflect the new opening balance.
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

        // A delivery/feed sale may be entered with a backdated transaction date.
        // Recalculate every row in chronological order so later balanceAfter
        // snapshots are updated as well. Without this, the account summary
        // (which reads the last persisted ledger balance) can disagree with the
        // customer statement (which rebuilds the balance from source transactions).
        recalculateCustomerLedger(customer.getId());
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

    @Transactional
    public List<LedgerEntryResponse> getCustomerLedger(Long customerId) {
        reconcileCustomerLedger(customerId);
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

    /** Opening payable + purchases - payments, computed from the real records (never a stale snapshot). */
    public BigDecimal getCurrentSupplierBalance(Supplier supplier) {
        return nz(supplier.getOpeningPayableBalance())
                .add(nz(purchaseRepository.sumPurchasesForSupplier(supplier.getId())))
                .subtract(nz(supplierPaymentRepository.sumPaidForSupplier(supplier.getId())));
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
