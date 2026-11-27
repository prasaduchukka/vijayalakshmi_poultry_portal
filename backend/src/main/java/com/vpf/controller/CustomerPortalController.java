package com.vpf.controller;

import com.vpf.security.AppUserDetails;
import com.vpf.service.CustomerService;
import com.vpf.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Customer-only endpoints. The customer id always comes from the authenticated account. */
@RestController
@RequestMapping("/api/customer-portal")
@RequiredArgsConstructor
public class CustomerPortalController {
    private final CustomerService customerService;
    private final LedgerService ledgerService;

    @GetMapping("/me")
    public Object me(Authentication authentication) {
        AppUserDetails user = (AppUserDetails) authentication.getPrincipal();
        if (user.getCustomerId() == null) throw new org.springframework.security.access.AccessDeniedException("Customer account is not linked.");
        return Map.of("customer", customerService.findById(user.getCustomerId()),
                "account", customerService.getAccountSummary(user.getCustomerId()));
    }

    @GetMapping("/ledger")
    public Object ledger(Authentication authentication) {
        AppUserDetails user = (AppUserDetails) authentication.getPrincipal();
        if (user.getCustomerId() == null) throw new org.springframework.security.access.AccessDeniedException("Customer account is not linked.");
        // Customers see only the last 10 days (today + previous 9), with the same balances as the full ledger.
        return ledgerService.getCustomerLedger(user.getCustomerId(), java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).minusDays(9));
    }
}
