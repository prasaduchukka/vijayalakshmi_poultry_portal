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

    @GetMapping("/api/supplier-ledger/{supplierId}")
    public Object supplierLedger(@PathVariable Long supplierId) {
        return ledgerService.getSupplierLedger(supplierId);
    }
}
