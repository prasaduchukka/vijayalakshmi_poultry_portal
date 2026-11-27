package com.vpf.repository;

import com.vpf.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select x from Customer x where x.id = :id")
    java.util.Optional<Customer> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);


    @org.springframework.data.jpa.repository.Query("select coalesce(sum(c.openingBalance), 0) from Customer c")
    BigDecimal sumAllOpeningBalances();
}
