package com.vpf.repository;

import com.vpf.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    List<CustomerOrder> findByCustomerIdOrderByOrderDateDesc(Long customerId);
    List<CustomerOrder> findAllByOrderByOrderDateDesc();
}
