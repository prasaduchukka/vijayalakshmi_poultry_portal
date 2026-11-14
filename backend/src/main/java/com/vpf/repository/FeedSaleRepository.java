package com.vpf.repository;

import com.vpf.entity.FeedSale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedSaleRepository extends JpaRepository<FeedSale, Long> {
    List<FeedSale> findByCustomerIdOrderBySaleDateDesc(Long customerId);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(f.amount), 0) from FeedSale f")
    java.math.BigDecimal sumAllAmounts();
}
