package com.vpf.repository;

import com.vpf.entity.SupplierPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    List<SupplierPayment> findBySupplierIdOrderByPaymentDateDesc(Long supplierId);

    @org.springframework.data.jpa.repository.Query(
        "select coalesce(sum(p.amount), 0) from SupplierPayment p where p.supplier.id = :supplierId")
    BigDecimal sumPaidForSupplier(Long supplierId);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(p.amount), 0) from SupplierPayment p")
    BigDecimal sumAllPaid();
}
