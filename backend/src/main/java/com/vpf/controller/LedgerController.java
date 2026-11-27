package com.vpf.controller;

import com.vpf.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping("/api/customer-ledger/{customerId}")
    public Object customerLedger(@PathVariable Long customerId) {
        return ledgerService.getCustomerLedger(customerId);
    }

    /** Admin audit: for every customer/supplier, records-based outstanding must equal the ledger. Read-only. */
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/ledger/verify")
    public Object verify() {
        return ledgerService.verifyAllBalances();
    }

    @GetMapping("/api/supplier-ledger/{supplierId}")
    public Object supplierLedger(@PathVariable Long supplierId) {
        return ledgerService.getSupplierLedger(supplierId);
    }
}
