package com.vpf.repository;

import com.vpf.entity.FeedSale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedSaleRepository extends JpaRepository<FeedSale, Long> {
    List<FeedSale> findByCustomerIdOrderBySaleDateDesc(Long customerId);
}
