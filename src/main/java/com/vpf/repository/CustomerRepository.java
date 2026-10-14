package com.vpf.repository;

import com.vpf.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(c.openingBalance), 0) from Customer c")
    BigDecimal sumAllOpeningBalances();
}
