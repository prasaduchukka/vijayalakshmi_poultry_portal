package com.vpf.repository;

import com.vpf.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select x from Supplier x where x.id = :id")
    java.util.Optional<Supplier> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);


    @org.springframework.data.jpa.repository.Query("select coalesce(sum(s.openingPayableBalance), 0) from Supplier s")
    BigDecimal sumAllOpeningPayableBalances();
}
