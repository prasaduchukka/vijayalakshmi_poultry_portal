package com.vpf.repository;

import com.vpf.entity.CustomerPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CustomerPaymentRepository extends JpaRepository<CustomerPayment, Long> {

    List<CustomerPayment> findByCustomerIdOrderByPaymentDateDesc(Long customerId);

    List<CustomerPayment> findByDeliveryId(Long deliveryId);

    List<CustomerPayment> findByPaymentDateBetween(LocalDate from, LocalDate to);

    @org.springframework.data.jpa.repository.Query(
        "select coalesce(sum(p.amount), 0) from CustomerPayment p where p.customer.id = :customerId")
    BigDecimal sumPaidForCustomer(Long customerId);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(p.amount), 0) from CustomerPayment p")
    BigDecimal sumAllPaid();
}
