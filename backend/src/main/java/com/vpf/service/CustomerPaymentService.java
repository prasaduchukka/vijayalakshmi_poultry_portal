package com.vpf.service;

import com.vpf.dto.CustomerPaymentRequest;
import com.vpf.dto.CustomerPaymentResponse;
import com.vpf.entity.Customer;
import com.vpf.entity.CustomerPayment;
import com.vpf.entity.Delivery;
import com.vpf.entity.enums.LedgerReferenceType;
import com.vpf.repository.CustomerPaymentRepository;
import com.vpf.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerPaymentService {

    private final CustomerPaymentRepository customerPaymentRepository;
    private final CustomerService customerService;
    private final LedgerService ledgerService;
    private final com.vpf.repository.DeliveryRepository deliveryRepository;

    private static final String WEIGHT_LOSS_MARKER = "AUTO_DELIVERY_ADJUSTMENT:WEIGHT_LOSS";
    private static final String MORTALITY_MARKER = "AUTO_DELIVERY_ADJUSTMENT:MORTALITY";

    @Transactional
    public CustomerPaymentResponse create(CustomerPaymentRequest req) {
        Customer customer = customerService.getOrThrow(req.getCustomerId());

        CustomerPayment payment = new CustomerPayment();
        payment.setCustomer(customer);
        payment.setPaymentDate(req.getPaymentDate());
        payment.setAmount(req.getAmount());
        payment.setPaymentMethod(req.getPaymentMethod());
        payment.setReferenceNumber(req.getReferenceNumber());
        payment.setNotes(req.getNotes());
        payment.setCreatedBy(req.getCreatedBy());
        customerPaymentRepository.save(payment);

        // Every payment is stored as its own transaction - never overwriting past payments.
        ledgerService.recordCustomerCredit(customer, req.getPaymentDate(), LedgerReferenceType.PAYMENT,
                payment.getId(), req.getAmount(),
                "Payment received (" + req.getPaymentMethod() + ")" + (req.getReferenceNumber() != null ? " Ref: " + req.getReferenceNumber() : ""));

        return toResponse(payment);
    }

    /** Creates a payment that is explicitly tied to a delivery. */
    @Transactional
    public CustomerPaymentResponse createForDelivery(Customer customer, Delivery delivery,
                                                      java.math.BigDecimal amount,
                                                      com.vpf.entity.enums.PaymentMethod method,
                                                      String createdBy) {
        if (amount == null || amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (delivery.getTotalAmount() != null && amount.compareTo(delivery.getTotalAmount()) > 0) {
            throw new BusinessRuleException("Payment for Delivery #" + delivery.getId()
                    + " cannot exceed the chicken sales amount of Rs." + delivery.getTotalAmount()
                    + ". Record any additional advance separately as a customer payment.");
        }
        CustomerPayment payment = new CustomerPayment();
        payment.setCustomer(customer);
        payment.setDelivery(delivery);
        payment.setPaymentDate(delivery.getDeliveryDate());
        payment.setAmount(amount);
        payment.setPaymentMethod(method != null ? method : com.vpf.entity.enums.PaymentMethod.CASH);
        payment.setNotes("Payment for delivery #" + delivery.getId());
        payment.setCreatedBy(createdBy);
        customerPaymentRepository.save(payment);
        ledgerService.recordCustomerCredit(customer, payment.getPaymentDate(), LedgerReferenceType.PAYMENT,
                payment.getId(), payment.getAmount(),
                "Payment received for Delivery #" + delivery.getId() + " (" + payment.getPaymentMethod() + ")");
        return toResponse(payment);
    }

    /** Prevents an edit from reducing a delivery below the money already received for it. */
    public void validateDeliveryPaymentTotal(Long deliveryId, java.math.BigDecimal newSalesAmount) {
        java.math.BigDecimal paid = customerPaymentRepository.findByDeliveryId(deliveryId).stream()
                .filter(p -> !isAutomaticDeliveryAdjustment(p))
                .map(CustomerPayment::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        if (newSalesAmount != null && paid.compareTo(newSalesAmount) > 0) {
            throw new BusinessRuleException("Delivery #" + deliveryId + " cannot be saved with a sales amount of Rs."
                    + newSalesAmount + " because Rs." + paid
                    + " has already been received against this delivery. Increase the sale or adjust the linked payment first.");
        }
    }

    /** Synchronize the delivery's single inline payment with the edited payment amount. */
    @Transactional
    public void synchronizeDeliveryPayment(Long deliveryId, Customer customer, LocalDate date,
            java.math.BigDecimal requestedAmount, com.vpf.entity.enums.PaymentMethod requestedMethod, String createdBy) {
        if (requestedAmount == null) return; // omitted means preserve existing payment(s)
        if (requestedAmount.compareTo(java.math.BigDecimal.ZERO) < 0)
            throw new BusinessRuleException("Payment amount cannot be negative.");
        List<CustomerPayment> linked = customerPaymentRepository.findByDeliveryId(deliveryId).stream()
                .filter(p -> !isAutomaticDeliveryAdjustment(p))
                .toList();
        if (linked.size() > 1) {
            throw new BusinessRuleException("Delivery #" + deliveryId + " has multiple linked payments. Review them in Payments before changing the inline payment amount.");
        }
        if (requestedAmount.compareTo(java.math.BigDecimal.ZERO) == 0) {
            if (!linked.isEmpty()) {
                CustomerPayment old = linked.get(0);
                ledgerService.deleteCustomerLedgerEntryAndRecalculate(old.getCustomer(), LedgerReferenceType.PAYMENT, old.getId());
                customerPaymentRepository.delete(old);
            }
            return;
        }
        CustomerPayment payment;
        if (linked.isEmpty()) {
            payment = new CustomerPayment();
            payment.setDelivery(deliveryRepositoryReference(deliveryId));
            payment.setCustomer(customer);
            payment.setPaymentMethod(requestedMethod != null ? requestedMethod : com.vpf.entity.enums.PaymentMethod.CASH);
            payment.setCreatedBy(createdBy);
            payment.setNotes("Payment for delivery #" + deliveryId);
        } else {
            payment = linked.get(0);
            if (!payment.getCustomer().getId().equals(customer.getId())) {
                ledgerService.deleteCustomerLedgerEntryAndRecalculate(payment.getCustomer(), LedgerReferenceType.PAYMENT, payment.getId());
                payment.setCustomer(customer);
            }
            if (requestedMethod != null) payment.setPaymentMethod(requestedMethod);
        }
        payment.setPaymentDate(date);
        payment.setAmount(requestedAmount);
        customerPaymentRepository.save(payment);
        ledgerService.replaceCustomerEntry(customer, LedgerReferenceType.PAYMENT, payment.getId(), date,
                null, requestedAmount, "Payment received for Delivery #" + deliveryId + " (" + payment.getPaymentMethod() + ")");
    }

    /**
     * Keeps the delivery's calculated customer compensations as separate payment
     * records and PAYMENT ledger credits. They are system-managed and are not
     * mixed with the optional cash/UPI amount entered by the operator.
     */
    @Transactional
    public void synchronizeDeliveryCompensation(Delivery delivery, Customer customer, String createdBy) {
        syncAutomaticAdjustment(delivery, customer, delivery.getWeightLossAmount(),
                WEIGHT_LOSS_MARKER, "Weight loss amount", createdBy);
        syncAutomaticAdjustment(delivery, customer, delivery.getMortalityAmount(),
                MORTALITY_MARKER, "Mortality amount", createdBy);
    }

    private void syncAutomaticAdjustment(Delivery delivery, Customer customer, java.math.BigDecimal amount,
                                         String marker, String label, String createdBy) {
        List<CustomerPayment> matches = customerPaymentRepository.findByDeliveryId(delivery.getId()).stream()
                .filter(p -> marker.equals(p.getNotes())).toList();
        if (matches.size() > 1) {
            throw new BusinessRuleException("Multiple automatic " + label.toLowerCase()
                    + " payments exist for Delivery #" + delivery.getId() + ". Please contact support before editing this delivery.");
        }
        CustomerPayment payment = matches.isEmpty() ? null : matches.get(0);
        java.math.BigDecimal safeAmount = amount == null ? java.math.BigDecimal.ZERO : amount;
        if (safeAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            if (payment != null) {
                ledgerService.deleteCustomerLedgerEntryAndRecalculate(payment.getCustomer(), LedgerReferenceType.PAYMENT, payment.getId());
                customerPaymentRepository.delete(payment);
            }
            return;
        }
        if (payment == null) {
            payment = new CustomerPayment();
            payment.setDelivery(delivery);
            payment.setCreatedBy(createdBy);
        } else if (!payment.getCustomer().getId().equals(customer.getId())) {
            ledgerService.deleteCustomerLedgerEntryAndRecalculate(payment.getCustomer(), LedgerReferenceType.PAYMENT, payment.getId());
        }
        payment.setCustomer(customer);
        payment.setPaymentDate(delivery.getDeliveryDate());
        payment.setAmount(safeAmount);
        payment.setPaymentMethod(com.vpf.entity.enums.PaymentMethod.OTHER);
        payment.setReferenceNumber(label + " - Delivery #" + delivery.getId());
        payment.setNotes(marker);
        customerPaymentRepository.save(payment);
        ledgerService.replaceCustomerEntry(customer, LedgerReferenceType.PAYMENT, payment.getId(),
                delivery.getDeliveryDate(), null, safeAmount, label + " for Delivery #" + delivery.getId());
    }

    private boolean isAutomaticDeliveryAdjustment(CustomerPayment payment) {
        return WEIGHT_LOSS_MARKER.equals(payment.getNotes()) || MORTALITY_MARKER.equals(payment.getNotes());
    }

    private String paymentLedgerDescription(CustomerPayment payment, Long deliveryId) {
        if (payment.getReferenceNumber() != null && !payment.getReferenceNumber().isBlank()) {
            return "Payment received - " + payment.getReferenceNumber();
        }
        return "Payment received for Delivery #" + deliveryId + " (" + payment.getPaymentMethod() + ")";
    }

    private Delivery deliveryRepositoryReference(Long deliveryId) {
        return deliveryRepository.getReferenceById(deliveryId);
    }

    public com.vpf.entity.enums.PaymentMethod paymentMethodForDelivery(Long deliveryId) {
        return customerPaymentRepository.findByDeliveryId(deliveryId).stream()
                .filter(p -> !isAutomaticDeliveryAdjustment(p))
                .findFirst().map(CustomerPayment::getPaymentMethod).orElse(null);
    }

    public java.math.BigDecimal totalPaidForDelivery(Long deliveryId) {
        return customerPaymentRepository.findByDeliveryId(deliveryId).stream()
                .filter(p -> !isAutomaticDeliveryAdjustment(p))
                .map(CustomerPayment::getAmount).filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    /** Deletes every payment attached to a delivery and its corresponding ledger rows. */
    @Transactional
    public void deleteForDelivery(Long deliveryId) {
        for (CustomerPayment payment : customerPaymentRepository.findByDeliveryId(deliveryId)) {
            ledgerService.deleteCustomerLedgerEntryAndRecalculate(
                    payment.getCustomer(), LedgerReferenceType.PAYMENT, payment.getId());
            customerPaymentRepository.delete(payment);
        }
        customerPaymentRepository.flush();
    }

    /**
     * A delivery edit can move the sale from Customer A to Customer B. Any
     * payment that was entered specifically for that delivery follows it.
     * Its old ledger row is removed from A and recreated for B.
     */
    @Transactional
    public void reassignDeliveryPayments(Long deliveryId, Customer newCustomer, java.time.LocalDate newDate) {
        for (CustomerPayment payment : customerPaymentRepository.findByDeliveryId(deliveryId)) {
            Customer oldCustomer = payment.getCustomer();
            if (!oldCustomer.getId().equals(newCustomer.getId())) {
                ledgerService.deleteCustomerLedgerEntryAndRecalculate(oldCustomer, LedgerReferenceType.PAYMENT, payment.getId());
                payment.setCustomer(newCustomer);
            }
            payment.setPaymentDate(newDate);
            customerPaymentRepository.save(payment);
            ledgerService.replaceCustomerEntry(newCustomer, LedgerReferenceType.PAYMENT, payment.getId(),
                    newDate, null, payment.getAmount(), paymentLedgerDescription(payment, deliveryId));
        }
    }

    /** Admin-only edit. Recalculates the customer's ledger from this entry forward. */
    @Transactional
    public CustomerPaymentResponse update(Long id, CustomerPaymentRequest req) {
        CustomerPayment payment = customerPaymentRepository.findById(id)
                .orElseThrow(() -> new com.vpf.exception.ResourceNotFoundException("Payment not found: " + id));
        if (isAutomaticDeliveryAdjustment(payment)) {
            throw new BusinessRuleException("This payment is calculated from the delivery's weight-loss/mortality details. Edit the delivery to change it.");
        }
        Customer customer = customerService.getOrThrow(req.getCustomerId());

        // A payment created from a delivery is part of that delivery's financial
        // chain. It cannot be manually reassigned to another customer/date from
        // the Payments screen; edit the Delivery instead so the sale and payment
        // move together.
        if (payment.getDelivery() != null) {
            Customer deliveryCustomer = payment.getDelivery().getCustomer();
            if (!deliveryCustomer.getId().equals(customer.getId())) {
                throw new BusinessRuleException("This payment belongs to Delivery #" + payment.getDelivery().getId()
                        + ". Change the delivery's customer instead of moving the payment separately.");
            }
            if (!payment.getDelivery().getDeliveryDate().equals(req.getPaymentDate())) {
                throw new BusinessRuleException("This payment belongs to Delivery #" + payment.getDelivery().getId()
                        + ". Its payment date follows the delivery date.");
            }
            if (req.getAmount().compareTo(payment.getDelivery().getTotalAmount()) > 0) {
                throw new BusinessRuleException("Payment cannot exceed Delivery #" + payment.getDelivery().getId()
                        + " chicken sales amount of Rs." + payment.getDelivery().getTotalAmount() + ".");
            }
        }

        payment.setCustomer(customer);
        payment.setPaymentDate(req.getPaymentDate());
        payment.setAmount(req.getAmount());
        payment.setPaymentMethod(req.getPaymentMethod());
        payment.setReferenceNumber(req.getReferenceNumber());
        payment.setNotes(req.getNotes());
        customerPaymentRepository.save(payment);

        ledgerService.replaceCustomerEntry(customer, LedgerReferenceType.PAYMENT, payment.getId(),
                req.getPaymentDate(), null, req.getAmount(),
                "Payment received (" + req.getPaymentMethod() + ")" + (req.getReferenceNumber() != null ? " Ref: " + req.getReferenceNumber() : ""));

        return toResponse(payment);
    }

    public List<CustomerPaymentResponse> findByCustomer(Long customerId) {
        return customerPaymentRepository.findByCustomerIdOrderByPaymentDateDesc(customerId).stream().map(this::toResponse).toList();
    }

    public List<CustomerPaymentResponse> findByDateRange(LocalDate from, LocalDate to) {
        return customerPaymentRepository.findByPaymentDateBetween(from, to).stream().map(this::toResponse).toList();
    }

    /** Permanently removes a payment and reverses its effect on the customer's ledger. Admin-only. */
    @Transactional
    public void delete(Long id) {
        CustomerPayment p = customerPaymentRepository.findById(id)
                .orElseThrow(() -> new com.vpf.exception.ResourceNotFoundException("Payment not found: " + id));
        if (isAutomaticDeliveryAdjustment(p)) {
            throw new BusinessRuleException("This payment is calculated from the delivery's weight-loss/mortality details. Edit the delivery instead of deleting this payment.");
        }
        ledgerService.deleteCustomerLedgerEntryAndRecalculate(p.getCustomer(), LedgerReferenceType.PAYMENT, id);
        customerPaymentRepository.delete(p);
    }

    public CustomerPaymentResponse toResponse(CustomerPayment p) {
        return CustomerPaymentResponse.builder()
                .id(p.getId())
                .customerId(p.getCustomer().getId())
                .customerName(p.getCustomer().getChickenCenterName())
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
