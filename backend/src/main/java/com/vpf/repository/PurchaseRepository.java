package com.vpf.repository;

import com.vpf.entity.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    List<Purchase> findBySupplierIdOrderByPurchaseDateDesc(Long supplierId);

    List<Purchase> findByPurchaseDateBetweenOrderByPurchaseDateAsc(LocalDate from, LocalDate to);

    List<Purchase> findByPurchaseDate(LocalDate date);

    List<Purchase> findByTripId(Long tripId);

    @org.springframework.data.jpa.repository.Query(
        "select coalesce(sum(p.purchaseAmount), 0) from Purchase p where p.purchaseDate between :from and :to")
    BigDecimal sumPurchasesBetween(LocalDate from, LocalDate to);

    @org.springframework.data.jpa.repository.Query(
        "select coalesce(sum(p.purchaseAmount), 0) from Purchase p where p.supplier.id = :supplierId")
    BigDecimal sumPurchasesForSupplier(Long supplierId);
}
