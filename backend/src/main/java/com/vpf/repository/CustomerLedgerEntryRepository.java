package com.vpf.repository;

import com.vpf.entity.CustomerLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerLedgerEntryRepository extends JpaRepository<CustomerLedgerEntry, Long> {

    List<CustomerLedgerEntry> findByCustomerIdOrderByEntryDateAscIdAsc(Long customerId);

    Optional<CustomerLedgerEntry> findTopByCustomerIdOrderByIdDesc(Long customerId);
}
