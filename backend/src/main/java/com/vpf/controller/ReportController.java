package com.vpf.controller;

import com.vpf.entity.Customer;
import com.vpf.entity.Supplier;
import com.vpf.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Every report the Admin can generate on demand, all as downloadable PDFs. */
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final PdfService pdfService;
    private final CustomerService customerService;
    private final LedgerService ledgerService;
    private final CustomerPaymentService customerPaymentService;
    private final SupplierService supplierService;
    private final PurchaseService purchaseService;
    private final SupplierPaymentService supplierPaymentService;
    private final DeliveryService deliveryService;
    private final ExpenseService expenseService;
    private final FeedSaleService feedSaleService;
    private final TripService tripService;

    @GetMapping("/api/reports/vehicle-trips")
    public ResponseEntity<byte[]> vehicleTripReport(
            @RequestParam String vehicleNumber,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws Exception {

        var trips = tripService.findByVehicleAndDateRange(vehicleNumber, from, to);

        java.util.Map<Long, java.util.List<com.vpf.dto.DeliveryResponse>> deliveriesByTrip = new java.util.HashMap<>();
        java.util.Map<Long, java.util.List<com.vpf.dto.PurchaseResponse>> purchasesByTrip = new java.util.HashMap<>();

        var allDeliveries = deliveryService.findByDateRange(from, to);
        var allPurchases = purchaseService.findByDateRange(from, to);

        for (var trip : trips) {
            deliveriesByTrip.put(trip.getId(), allDeliveries.stream()
                    .filter(d -> trip.getId().equals(d.getTripId())).toList());
            purchasesByTrip.put(trip.getId(), allPurchases.stream()
                    .filter(p -> trip.getId().equals(p.getTripId())).toList());
        }

        byte[] pdf = pdfService.vehicleTripReport(vehicleNumber, from, to, trips, deliveriesByTrip, purchasesByTrip);
        return pdfResponse(pdf, "vehicle-" + vehicleNumber.replaceAll("\\s+", "-") + "-" + from + "-to-" + to + ".pdf");
    }

    @GetMapping("/api/reports/customer-statement/{customerId}")
    public ResponseEntity<byte[]> customerStatement(
            @PathVariable Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws Exception {

        Customer customer = customerService.getOrThrow(customerId);
        var deliveries = deliveryService.findByCustomer(customerId).stream()
                .filter(d -> !d.getDeliveryDate().isBefore(from) && !d.getDeliveryDate().isAfter(to)).toList();
        var payments = customerPaymentService.findByCustomer(customerId).stream()
                .filter(p -> !p.getPaymentDate().isBefore(from) && !p.getPaymentDate().isAfter(to)).toList();
        var feedSales = feedSaleService.findByCustomer(customerId).stream()
                .filter(f -> !f.getSaleDate().isBefore(from) && !f.getSaleDate().isAfter(to)).toList();

        byte[] pdf = pdfService.customerStatement(customer, from, to, deliveries, payments, feedSales, ledgerService.getCustomerBalanceBefore(customer, from));

        return pdfResponse(pdf, "customer-statement-" + customerId + ".pdf");
    }

    /** Every customer's statement in one combined PDF, each starting on a fresh page. */
    @GetMapping("/api/reports/customer-statement/all")
    public ResponseEntity<byte[]> allCustomerStatements(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws Exception {

        var customerResponses = customerService.findAll();
        var customers = customerResponses.stream()
                .map(c -> customerService.getOrThrow(c.getId())).toList();

        java.util.Map<Long, java.util.List<com.vpf.dto.DeliveryResponse>> deliveriesByCustomer = new java.util.HashMap<>();
        java.util.Map<Long, java.util.List<com.vpf.dto.CustomerPaymentResponse>> paymentsByCustomer = new java.util.HashMap<>();
        java.util.Map<Long, java.util.List<com.vpf.dto.FeedSaleResponse>> feedSalesByCustomer = new java.util.HashMap<>();

        java.util.Map<Long, java.math.BigDecimal> openingByCustomer = new java.util.HashMap<>();
        for (Customer c : customers) {
            openingByCustomer.put(c.getId(), ledgerService.getCustomerBalanceBefore(c, from));
            deliveriesByCustomer.put(c.getId(), deliveryService.findByCustomer(c.getId()).stream()
                    .filter(d -> !d.getDeliveryDate().isBefore(from) && !d.getDeliveryDate().isAfter(to)).toList());
            paymentsByCustomer.put(c.getId(), customerPaymentService.findByCustomer(c.getId()).stream()
                    .filter(p -> !p.getPaymentDate().isBefore(from) && !p.getPaymentDate().isAfter(to)).toList());
            feedSalesByCustomer.put(c.getId(), feedSaleService.findByCustomer(c.getId()).stream()
                    .filter(f -> !f.getSaleDate().isBefore(from) && !f.getSaleDate().isAfter(to)).toList());
        }

        byte[] pdf = pdfService.allCustomerStatements(customers, from, to, deliveriesByCustomer, paymentsByCustomer, feedSalesByCustomer, openingByCustomer);
        return pdfResponse(pdf, "all-customer-statements-" + from + "-to-" + to + ".pdf");
    }

    /** Purchase history (vehicle / driver / helper) for one supplier, optionally limited to a date range. */
    @GetMapping("/api/reports/supplier-purchase/{supplierId}")
    public ResponseEntity<byte[]> supplierPurchase(
            @PathVariable Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws Exception {
        Supplier supplier = supplierService.getOrThrow(supplierId);
        var purchases = purchaseService.findBySupplier(supplierId).stream()
                .filter(p -> from == null || !p.getPurchaseDate().isBefore(from))
                .filter(p -> to == null || !p.getPurchaseDate().isAfter(to))
                .toList();
        byte[] pdf = pdfService.supplierPurchase(supplier.getSupplierName(), purchases, from, to);
        return pdfResponse(pdf, "supplier-purchase-" + supplierId + ".pdf");
    }

    /** Purchase history (vehicle / driver / helper) for every supplier together. */
    @GetMapping("/api/reports/supplier-purchase/all")
    public ResponseEntity<byte[]> allSuppliersPurchase(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws Exception {
        var purchases = purchaseService.findByDateRange(from, to);
        byte[] pdf = pdfService.supplierPurchase(null, purchases, from, to);
        return pdfResponse(pdf, "all-suppliers-purchase-" + from + "-to-" + to + ".pdf");
    }

    @GetMapping("/api/reports/expenses")
    public ResponseEntity<byte[]> expenseReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws Exception {
        var expenses = expenseService.findByDateRange(from, to);
        byte[] pdf = pdfService.expenseReport(from, to, expenses);
        return pdfResponse(pdf, "expenses-" + from + "-to-" + to + ".pdf");
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
