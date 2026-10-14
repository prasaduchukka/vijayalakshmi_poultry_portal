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
        if (delivery.getSalesAmount() != null && amount.compareTo(delivery.getSalesAmount()) > 0) {
            throw new BusinessRuleException("Payment for Delivery #" + delivery.getId()
                    + " cannot exceed the delivery sales amount of Rs." + delivery.getSalesAmount()
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
                .map(CustomerPayment::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        if (newSalesAmount != null && paid.compareTo(newSalesAmount) > 0) {
            throw new BusinessRuleException("Delivery #" + deliveryId + " cannot be saved with a sales amount of Rs."
                    + newSalesAmount + " because Rs." + paid
                    + " has already been received against this delivery. Increase the sale or adjust the linked payment first.");
        }
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
                    newDate, null, payment.getAmount(),
                    "Payment received for Delivery #" + deliveryId + " (" + payment.getPaymentMethod() + ")");
        }
    }

    /** Admin-only edit. Recalculates the customer's ledger from this entry forward. */
    @Transactional
    public CustomerPaymentResponse update(Long id, CustomerPaymentRequest req) {
        CustomerPayment payment = customerPaymentRepository.findById(id)
                .orElseThrow(() -> new com.vpf.exception.ResourceNotFoundException("Payment not found: " + id));
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
            if (req.getAmount().compareTo(payment.getDelivery().getSalesAmount()) > 0) {
                throw new BusinessRuleException("Payment cannot exceed Delivery #" + payment.getDelivery().getId()
                        + " sales amount of Rs." + payment.getDelivery().getSalesAmount() + ".");
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
