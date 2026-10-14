package com.vpf.repository;

import com.vpf.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    List<Delivery> findByCustomerIdOrderByDeliveryDateDesc(Long customerId);

    List<Delivery> findByDeliveryDateBetweenOrderByDeliveryDateAsc(LocalDate from, LocalDate to);

    List<Delivery> findByDeliveryDate(LocalDate date);

    @org.springframework.data.jpa.repository.Query(
        "select coalesce(sum(d.salesAmount), 0) from Delivery d where d.deliveryDate between :from and :to")
    BigDecimal sumSalesBetween(LocalDate from, LocalDate to);

    @org.springframework.data.jpa.repository.Query(
        "select coalesce(sum(d.salesAmount), 0) from Delivery d where d.customer.id = :customerId")
    BigDecimal sumSalesForCustomer(Long customerId);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(d.salesAmount), 0) from Delivery d")
    BigDecimal sumAllSales();

    long countByCustomerId(Long customerId);

    List<Delivery> findByTripId(Long tripId);

    List<Delivery> findByOrderId(Long orderId);
}
