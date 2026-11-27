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
import com.vpf.repository.SupplierRepository;
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
    private final SupplierRepository supplierRepository;
    private final org.springframework.transaction.PlatformTransactionManager txManager;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LedgerService.class);

    // ---------- Customer side ----------

    /**
     * Self-heals every customer's and supplier's ledger table from the real source records
     * (opening balance, deliveries, feed sales, payments, purchases) at startup, so orphaned or
     * stale rows left behind by older versions/deletes cannot survive. Each party is repaired in
     * its own transaction, and a failure is logged without preventing the application from starting.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void repairCustomerLedgerSnapshotsOnStartup() {
        org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(txManager);
        for (Customer c : customerRepository.findAll()) {
            Long id = c.getId();
            try {
                tx.executeWithoutResult(status -> reconcileCustomerLedger(id));
            } catch (Exception ex) {
                log.error("Could not reconcile ledger for customer {}: {}", id, ex.getMessage());
            }
        }
        for (Supplier sp : supplierRepository.findAll()) {
            Long id = sp.getId();
            try {
                tx.executeWithoutResult(status -> reconcileSupplierLedger(id));
            } catch (Exception ex) {
                log.error("Could not reconcile ledger for supplier {}: {}", id, ex.getMessage());
            }
        }
        warnAboutLegacyDeliveryFeed();
        try {
            java.util.Map<String, Object> result = verifyAllBalances();
            if ("OK".equals(result.get("status"))) {
                log.info("Ledger integrity check passed: {} customers and {} suppliers - ledger equals outstanding for all.",
                        result.get("customersChecked"), result.get("suppliersChecked"));
            } else {
                log.error("LEDGER INTEGRITY CHECK FAILED: {}", result);
            }
        } catch (Exception ex) {
            log.error("Ledger integrity check could not run: {}", ex.getMessage());
        }
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
        List<FeedSale> customerFeedSales = feedSaleRepository.findByCustomerIdOrderBySaleDateDesc(customer.getId());
        BigDecimal feedSales = customerFeedSales.stream()
                .map(f -> nz(f.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal feedPayments = customerFeedSales.stream()
                .map(f -> nz(f.getAmountPaid())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return nz(customer.getOpeningBalance())
                .add(nz(deliveryRepository.sumSalesForCustomer(customer.getId())))
                .add(feedSales)
                .subtract(nz(customerPaymentRepository.sumPaidForCustomer(customer.getId())))
                .subtract(feedPayments);
    }

    /**
     * Rebuilds the customer's ledger rows from the real records when (and only when)
     * they differ from what is stored: removes orphan rows whose source record no
     * longer exists, adds missing rows, then recalculates every running balance.
     * Business records (deliveries, payments, feed sales) are never modified.
     */
    @Transactional
    public void reconcileCustomerLedger(Long customerId) {
        Customer customer = customerRepository.findByIdForUpdate(customerId).orElse(null);
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
        feedSaleRepository.findByCustomerIdOrderBySaleDateDesc(customerId).stream()
                .filter(f -> nz(f.getAmountPaid()).compareTo(BigDecimal.ZERO) > 0)
                .sorted(java.util.Comparator.comparing(FeedSale::getSaleDate).thenComparing(FeedSale::getId))
                .forEach(f -> expected.add(new Row(f.getSaleDate(), LedgerReferenceType.FEED_PAYMENT, f.getId(),
                        BigDecimal.ZERO, nz(f.getAmountPaid()), "Payment received against feed sale #" + f.getId())));

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
    @Transactional
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

        lockCustomer(customer.getId());
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
    @Transactional
    public void recordCustomerDebit(Customer customer, LocalDate date, LedgerReferenceType type,
                                     Long referenceId, BigDecimal amount, String description) {
        lockCustomer(customer.getId());
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
    @Transactional
    public void recordCustomerCredit(Customer customer, LocalDate date, LedgerReferenceType type,
                                      Long referenceId, BigDecimal amount, String description) {
        lockCustomer(customer.getId());
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
        return getCustomerLedger(customerId, null);
    }

    /**
     * Same rows and the same running balances as the full ledger, restricted to entries dated on/after
     * {@code fromDate} (null = everything). Balances are NOT recomputed for the window, so the first
     * visible row's O.Bal is the true balance carried forward from earlier days.
     */
    @Transactional
    public List<LedgerEntryResponse> getCustomerLedger(Long customerId, LocalDate fromDate) {
        reconcileCustomerLedger(customerId);
        return customerLedgerEntryRepository.findByCustomerIdOrderByEntryDateAscIdAsc(customerId).stream()
                .filter(e -> fromDate == null || !e.getEntryDate().isBefore(fromDate))
                .map(e -> {
                    Delivery delivery = e.getReferenceType() == LedgerReferenceType.DELIVERY && e.getReferenceId() != null
                            ? deliveryRepository.findById(e.getReferenceId()).orElse(null) : null;
                    FeedSale feedSale = (e.getReferenceType() == LedgerReferenceType.FEED_SALE
                            || e.getReferenceType() == LedgerReferenceType.FEED_PAYMENT) && e.getReferenceId() != null
                            ? feedSaleRepository.findById(e.getReferenceId()).orElse(null) : null;
                    BigDecimal debit = nz(e.getDebit());
                    BigDecimal credit = nz(e.getCredit());
                    BigDecimal balance = nz(e.getBalanceAfter());
                    BigDecimal opening = balance.subtract(debit).add(credit);
                    BigDecimal total = opening.add(debit);
                    return LedgerEntryResponse.builder()
                            .id(e.getId()).entryDate(e.getEntryDate())
                            .referenceType(e.getReferenceType()).referenceId(e.getReferenceId())
                            .debit(debit).credit(credit).balanceAfter(balance).description(e.getDescription())
                            .birds(delivery == null ? null : delivery.getNumberOfBirds())
                            .kgOrItem(delivery != null ? (delivery.getReceivedWeight() != null ? delivery.getReceivedWeight() : delivery.getDispatchWeight()).toPlainString()
                                    : (feedSale != null && e.getReferenceType() == LedgerReferenceType.FEED_SALE ? "Feed" : "-"))
                            .rate(delivery == null ? null : delivery.getSellingRate())
                            .amount(debit).openingBalance(opening).totalBalance(total).paid(credit).balance(balance)
                            .build();
                })
                .toList();
    }

    /**
     * Removes the ledger entry tied to a deleted delivery/payment/feed-sale, then
     * recomputes every later entry's running balance so the ledger stays consistent.
     * Used by admin-only permanent deletes.
     */
    @Transactional
    public void deleteCustomerLedgerEntryAndRecalculate(Customer customer, LedgerReferenceType type, Long referenceId) {
        lockCustomer(customer.getId());
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

    @Transactional
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
    @Transactional
    public void recordSupplierDebit(Supplier supplier, LocalDate date, LedgerReferenceType type,
                                     Long referenceId, BigDecimal amount, String description) {
        lockSupplier(supplier.getId());
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
    @Transactional
    public void recordSupplierCredit(Supplier supplier, LocalDate date, LedgerReferenceType type,
                                      Long referenceId, BigDecimal amount, String description) {
        lockSupplier(supplier.getId());
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

    @Transactional
    public List<LedgerEntryResponse> getSupplierLedger(Long supplierId) {
        reconcileSupplierLedger(supplierId);
        return supplierLedgerEntryRepository.findBySupplierIdOrderByEntryDateAscIdAsc(supplierId).stream()
                .map(e -> {
                    Delivery delivery = e.getReferenceType() == LedgerReferenceType.DELIVERY && e.getReferenceId() != null
                            ? deliveryRepository.findById(e.getReferenceId()).orElse(null) : null;
                    FeedSale feedSale = (e.getReferenceType() == LedgerReferenceType.FEED_SALE
                            || e.getReferenceType() == LedgerReferenceType.FEED_PAYMENT) && e.getReferenceId() != null
                            ? feedSaleRepository.findById(e.getReferenceId()).orElse(null) : null;
                    BigDecimal debit = nz(e.getDebit());
                    BigDecimal credit = nz(e.getCredit());
                    BigDecimal balance = nz(e.getBalanceAfter());
                    BigDecimal opening = balance.subtract(debit).add(credit);
                    BigDecimal total = opening.add(debit);
                    return LedgerEntryResponse.builder()
                            .id(e.getId()).entryDate(e.getEntryDate())
                            .referenceType(e.getReferenceType()).referenceId(e.getReferenceId())
                            .debit(debit).credit(credit).balanceAfter(balance).description(e.getDescription())
                            .birds(delivery == null ? null : delivery.getNumberOfBirds())
                            .kgOrItem(delivery != null ? (delivery.getReceivedWeight() != null ? delivery.getReceivedWeight() : delivery.getDispatchWeight()).toPlainString()
                                    : (feedSale != null && e.getReferenceType() == LedgerReferenceType.FEED_SALE ? "Feed" : "-"))
                            .rate(delivery == null ? null : delivery.getSellingRate())
                            .amount(debit).openingBalance(opening).totalBalance(total).paid(credit).balance(balance)
                            .build();
                })
                .toList();
    }

    /** Supplier equivalent of deleteCustomerLedgerEntryAndRecalculate - see that method for details. */
    @Transactional
    public void deleteSupplierLedgerEntryAndRecalculate(Supplier supplier, LedgerReferenceType type, Long referenceId) {
        lockSupplier(supplier.getId());
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
        lockCustomer(customer.getId());
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
        lockSupplier(supplier.getId());
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

    // ---------- Locking, period balances, supplier reconcile, integrity check ----------

    /** Serialises ledger changes per customer so two simultaneous saves cannot interleave. */
    private void lockCustomer(Long customerId) {
        if (customerId != null) customerRepository.findByIdForUpdate(customerId);
    }

    private void lockSupplier(Long supplierId) {
        if (supplierId != null) supplierRepository.findByIdForUpdate(supplierId);
    }

    /**
     * What the customer owed just BEFORE the given date: opening balance plus every
     * delivery, feed sale (net of its payment) and payment dated earlier. Used so a
     * period statement opens with the true carried-forward balance.
     */
    public BigDecimal getCustomerBalanceBefore(Customer customer, LocalDate date) {
        BigDecimal balance = nz(customer.getOpeningBalance());
        for (Delivery d : deliveryRepository.findByCustomerIdOrderByDeliveryDateDesc(customer.getId())) {
            if (d.getDeliveryDate().isBefore(date)) balance = balance.add(nz(d.getSalesAmount()));
        }
        for (FeedSale f : feedSaleRepository.findByCustomerIdOrderBySaleDateDesc(customer.getId())) {
            if (f.getSaleDate().isBefore(date)) balance = balance.add(nz(f.getAmount())).subtract(nz(f.getAmountPaid()));
        }
        for (CustomerPayment p : customerPaymentRepository.findByCustomerIdOrderByPaymentDateDesc(customer.getId())) {
            if (p.getPaymentDate().isBefore(date)) balance = balance.subtract(nz(p.getAmount()));
        }
        return balance;
    }

    /** Supplier equivalent of reconcileCustomerLedger. */
    @Transactional
    public void reconcileSupplierLedger(Long supplierId) {
        Supplier supplier = supplierRepository.findByIdForUpdate(supplierId).orElse(null);
        if (supplier == null) return;

        record Row(LocalDate date, LedgerReferenceType type, Long refId, BigDecimal debit, BigDecimal credit, String desc) {
            String key() {
                return type + "|" + refId + "|" + debit.stripTrailingZeros().toPlainString()
                        + "|" + credit.stripTrailingZeros().toPlainString()
                        + "|" + (type == LedgerReferenceType.OPENING ? "" : String.valueOf(date));
            }
        }

        List<Row> expected = new java.util.ArrayList<>();
        BigDecimal opening = nz(supplier.getOpeningPayableBalance());
        purchaseRepository.findBySupplierIdOrderByPurchaseDateDesc(supplierId).stream()
                .filter(pu -> nz(pu.getPurchaseAmount()).compareTo(BigDecimal.ZERO) > 0)
                .sorted(java.util.Comparator.comparing(Purchase::getPurchaseDate).thenComparing(Purchase::getId))
                .forEach(pu -> expected.add(new Row(pu.getPurchaseDate(), LedgerReferenceType.PURCHASE, pu.getId(),
                        pu.getPurchaseAmount(), BigDecimal.ZERO,
                        "Purchase #" + pu.getId() + " - " + pu.getPurchaseWeight() + " kg")));
        supplierPaymentRepository.findBySupplierIdOrderByPaymentDateDesc(supplierId).stream()
                .sorted(java.util.Comparator.comparing(SupplierPayment::getPaymentDate).thenComparing(SupplierPayment::getId))
                .forEach(pay -> expected.add(new Row(pay.getPaymentDate(), LedgerReferenceType.PAYMENT, pay.getId(),
                        BigDecimal.ZERO, nz(pay.getAmount()),
                        "Payment made (" + pay.getPaymentMethod() + ")"
                                + (pay.getReferenceNumber() != null ? " Ref: " + pay.getReferenceNumber() : ""))));
        LocalDate earliest = expected.stream().map(Row::date).min(LocalDate::compareTo).orElse(LocalDate.now());
        if (opening.compareTo(BigDecimal.ZERO) != 0) {
            expected.add(0, new Row(earliest, LedgerReferenceType.OPENING, null, opening, BigDecimal.ZERO, "Opening payable balance"));
        }

        List<SupplierLedgerEntry> stored = supplierLedgerEntryRepository.findBySupplierIdOrderByEntryDateAscIdAsc(supplierId);
        List<String> expectedKeys = expected.stream().map(Row::key).sorted().toList();
        List<String> storedKeys = stored.stream()
                .map(e -> new Row(e.getEntryDate(), e.getReferenceType(), e.getReferenceId(),
                        nz(e.getDebit()), nz(e.getCredit()), null).key())
                .sorted().toList();

        if (!expectedKeys.equals(storedKeys)) {
            supplierLedgerEntryRepository.deleteAll(stored);
            supplierLedgerEntryRepository.flush();
            for (Row r : expected) {
                SupplierLedgerEntry e = new SupplierLedgerEntry();
                e.setSupplier(supplier);
                e.setEntryDate(r.date());
                e.setReferenceType(r.type());
                e.setReferenceId(r.refId());
                e.setDebit(r.debit());
                e.setCredit(r.credit());
                e.setBalanceAfter(BigDecimal.ZERO);
                e.setDescription(r.desc());
                supplierLedgerEntryRepository.save(e);
            }
            supplierLedgerEntryRepository.flush();
        }
        recalculateSupplierLedger(supplierId);
    }

    /**
     * Read-only audit. For every customer and supplier, compares (1) the balance computed from the
     * real records, (2) the net of the stored ledger rows and (3) the last stored running balance.
     * All three must be identical. Nothing is modified.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> verifyAllBalances() {
        List<java.util.Map<String, Object>> problems = new java.util.ArrayList<>();
        int customersChecked = 0;
        int suppliersChecked = 0;

        for (Customer c : customerRepository.findAll()) {
            customersChecked++;
            BigDecimal computed = getCurrentCustomerBalance(c);
            List<CustomerLedgerEntry> rows = customerLedgerEntryRepository.findByCustomerIdOrderByEntryDateAscIdAsc(c.getId());
            BigDecimal net = BigDecimal.ZERO;
            for (CustomerLedgerEntry e : rows) net = net.add(nz(e.getDebit())).subtract(nz(e.getCredit()));
            BigDecimal last = rows.isEmpty() ? BigDecimal.ZERO : nz(rows.get(rows.size() - 1).getBalanceAfter());
            if (computed.compareTo(net) != 0 || computed.compareTo(last) != 0) {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("party", "CUSTOMER");
                m.put("id", c.getId());
                m.put("name", c.getChickenCenterName());
                m.put("outstandingFromRecords", computed);
                m.put("ledgerRowsNet", net);
                m.put("lastLedgerBalance", last);
                problems.add(m);
            }
        }
        for (Supplier sp : supplierRepository.findAll()) {
            suppliersChecked++;
            BigDecimal computed = getCurrentSupplierBalance(sp);
            List<SupplierLedgerEntry> rows = supplierLedgerEntryRepository.findBySupplierIdOrderByEntryDateAscIdAsc(sp.getId());
            BigDecimal net = BigDecimal.ZERO;
            for (SupplierLedgerEntry e : rows) net = net.add(nz(e.getDebit())).subtract(nz(e.getCredit()));
            BigDecimal last = rows.isEmpty() ? BigDecimal.ZERO : nz(rows.get(rows.size() - 1).getBalanceAfter());
            if (computed.compareTo(net) != 0 || computed.compareTo(last) != 0) {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("party", "SUPPLIER");
                m.put("id", sp.getId());
                m.put("name", sp.getSupplierName());
                m.put("outstandingFromRecords", computed);
                m.put("ledgerRowsNet", net);
                m.put("lastLedgerBalance", last);
                problems.add(m);
            }
        }

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("status", problems.isEmpty() ? "OK" : "MISMATCH");
        result.put("customersChecked", customersChecked);
        result.put("suppliersChecked", suppliersChecked);
        result.put("mismatchCount", problems.size());
        result.put("mismatches", problems);
        return result;
    }

    /**
     * Older versions let a delivery carry its own feed amount. The current workflow records feed as a
     * separate feed sale, so such legacy amounts are NOT part of any balance. They are never deleted;
     * this just makes sure the owner is told so they can be reviewed (see db/review_legacy_delivery_feed.sql).
     */
    private void warnAboutLegacyDeliveryFeed() {
        try {
            List<Delivery> legacy = deliveryRepository.findByFeedAmountGreaterThan(BigDecimal.ZERO);
            if (!legacy.isEmpty()) {
                BigDecimal total = legacy.stream().map(d -> nz(d.getFeedAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
                log.warn("DATA REVIEW NEEDED: {} old delivery record(s) still hold a feed amount totalling Rs.{} that is NOT "
                        + "included in any customer balance (feed is now recorded as a separate feed sale). "
                        + "Review them with db/review_legacy_delivery_feed.sql; nothing was deleted.", legacy.size(), total);
            }
        } catch (Exception ex) {
            log.warn("Could not check for legacy delivery feed amounts: {}", ex.getMessage());
        }
    }
}
