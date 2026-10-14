package com.vpf.service;

import com.vpf.dto.*;
import com.vpf.entity.Customer;
import com.vpf.exception.ResourceNotFoundException;
import com.vpf.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final DeliveryRepository deliveryRepository;
    private final CustomerPaymentRepository customerPaymentRepository;
    private final CustomerLedgerEntryRepository customerLedgerEntryRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final FeedSaleRepository feedSaleRepository;
    private final LedgerService ledgerService;

    @Transactional
    public CustomerResponse create(CustomerRequest req) {
        Customer c = new Customer();
        apply(c, req);
        customerRepository.save(c);
        ledgerService.recordCustomerOpeningBalance(c);
        return toResponse(c);
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest req) {
        Customer c = getOrThrow(id);
        apply(c, req);
        BigDecimal requestedOpeningBalance = req.getOpeningBalance() == null ? BigDecimal.ZERO : req.getOpeningBalance();
        if (c.getOpeningBalance() == null || c.getOpeningBalance().compareTo(requestedOpeningBalance) != 0) {
            c.setOpeningBalance(requestedOpeningBalance);
            ledgerService.updateCustomerOpeningBalance(c, requestedOpeningBalance);
        }
        customerRepository.save(c);
        return toResponse(c);
    }

    /**
     * Permanently deletes a customer and every record tied to them (deliveries,
     * payments, feed sales, orders, ledger entries). Admin-only, irreversible -
     * the frontend must get explicit confirmation before calling this.
     */
    @Transactional
    public void delete(Long id) {
        Customer c = getOrThrow(id);
        // Payment rows may now reference deliveries. Remove payments before their
        // parent deliveries, then remove all ledger/history rows.
        customerPaymentRepository.deleteAll(customerPaymentRepository.findByCustomerIdOrderByPaymentDateDesc(id));
        feedSaleRepository.deleteAll(feedSaleRepository.findByCustomerIdOrderBySaleDateDesc(id));
        deliveryRepository.deleteAll(deliveryRepository.findByCustomerIdOrderByDeliveryDateDesc(id));
        customerLedgerEntryRepository.deleteAll(customerLedgerEntryRepository.findByCustomerIdOrderByEntryDateAscIdAsc(id));
        customerOrderRepository.deleteAll(customerOrderRepository.findByCustomerIdOrderByOrderDateDesc(id));
        customerRepository.delete(c);
    }

    public List<CustomerResponse> findAll() {
        return customerRepository.findAll().stream().map(this::toResponse).toList();
    }

    public CustomerResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    public Customer getOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
    }

    public CustomerAccountSummary getAccountSummary(Long id) {
        Customer c = getOrThrow(id);
        long totalDeliveries = deliveryRepository.countByCustomerId(id);
        long totalBoxes = deliveryRepository.findByCustomerIdOrderByDeliveryDateDesc(id).stream()
                .mapToLong(d -> d.getNumberOfBoxes() == null ? 0 : d.getNumberOfBoxes()).sum();
        BigDecimal totalReceivedWeight = deliveryRepository.findByCustomerIdOrderByDeliveryDateDesc(id).stream()
                .map(d -> d.getReceivedWeight() == null ? BigDecimal.ZERO : d.getReceivedWeight())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSales = deliveryRepository.sumSalesForCustomer(id);
        BigDecimal totalPaid = customerPaymentRepository.sumPaidForCustomer(id);
        BigDecimal outstanding = ledgerService.getCurrentCustomerBalance(c);

        return CustomerAccountSummary.builder()
                .customer(toResponse(c))
                .currentOutstandingBalance(outstanding)
                .totalDeliveries(totalDeliveries)
                .totalBoxes(totalBoxes)
                .totalReceivedWeight(totalReceivedWeight)
                .totalSales(totalSales)
                .totalPaid(totalPaid)
                .build();
    }

    private void apply(Customer c, CustomerRequest req) {
        c.setChickenCenterName(req.getChickenCenterName());
        c.setOwnerContactPerson(req.getOwnerContactPerson());
        c.setPhoneNumber(req.getPhoneNumber());
        c.setAddress(req.getAddress());
        if (c.getId() == null) {
            c.setOpeningBalance(req.getOpeningBalance() == null ? BigDecimal.ZERO : req.getOpeningBalance());
        }
        c.setStatus(req.getStatus());
        c.setNotes(req.getNotes());
    }

    private CustomerResponse toResponse(Customer c) {
        return CustomerResponse.builder()
                .id(c.getId())
                .chickenCenterName(c.getChickenCenterName())
                .ownerContactPerson(c.getOwnerContactPerson())
                .phoneNumber(c.getPhoneNumber())
                .address(c.getAddress())
                .openingBalance(c.getOpeningBalance())
                .status(c.getStatus())
                .notes(c.getNotes())
                .createdDate(c.getCreatedDate())
                .updatedDate(c.getUpdatedDate())
                .build();
    }
}
