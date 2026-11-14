package com.vpf.repository;

import com.vpf.entity.SupplierLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierLedgerEntryRepository extends JpaRepository<SupplierLedgerEntry, Long> {

    List<SupplierLedgerEntry> findBySupplierIdOrderByEntryDateAscIdAsc(Long supplierId);

    Optional<SupplierLedgerEntry> findTopBySupplierIdOrderByIdDesc(Long supplierId);
}
