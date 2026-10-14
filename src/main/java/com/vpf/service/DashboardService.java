package com.vpf.service;

import com.vpf.dto.DashboardResponse;
import com.vpf.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CustomerOrderRepository orderRepository;
    private final DeliveryRepository deliveryRepository;
    private final PurchaseRepository purchaseRepository;
    private final ExpenseRepository expenseRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final CustomerPaymentRepository customerPaymentRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final OrderService orderService;
    private final DeliveryService deliveryService;
    private final CustomerPaymentService customerPaymentService;

    public DashboardResponse build() {
        LocalDate today = LocalDate.now();

        long todaysOrders = orderRepository.findAllByOrderByOrderDateDesc().stream()
                .filter(o -> o.getOrderDate().equals(today)).count();
        long todaysDeliveries = deliveryRepository.findByDeliveryDate(today).size();
        var todaysSales = deliveryRepository.findByDeliveryDate(today).stream()
                .map(d -> d.getSalesAmount()).filter(java.util.Objects::nonNull).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        long todaysPurchases = purchaseRepository.findByPurchaseDate(today).size();
        var todaysExpenses = expenseRepository.findByExpenseDate(today).stream()
                .map(e -> e.getAmount()).filter(java.util.Objects::nonNull).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        var todaysPurchaseCost = purchaseRepository.findByPurchaseDate(today).stream()
                .map(p -> p.getPurchaseAmount()).filter(java.util.Objects::nonNull).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        var customerOutstandingTotal = nz(customerRepository.sumAllOpeningBalances())
                .add(nz(deliveryRepository.sumAllSales()))
                .subtract(nz(customerPaymentRepository.sumAllPaid()));

        var supplierOutstandingTotal = nz(supplierRepository.sumAllOpeningPayableBalances())
                .add(nz(purchaseRepository.sumPurchasesBetween(LocalDate.of(2000, 1, 1), LocalDate.now().plusYears(50))))
                .subtract(nz(supplierPaymentRepository.sumAllPaid()));

        var estimatedProfitToday = todaysSales.subtract(todaysPurchaseCost).subtract(todaysExpenses);

        List<com.vpf.dto.OrderResponse> pendingOrders = orderService.findPending();

        List<com.vpf.dto.DeliveryResponse> recentDeliveries = deliveryRepository.findAll().stream()
                .sorted(Comparator.comparing(d -> ((com.vpf.entity.Delivery) d).getCreatedDate()).reversed())
                .limit(10)
                .map(deliveryService::toResponse)
                .toList();

        List<com.vpf.dto.CustomerPaymentResponse> recentPayments = customerPaymentRepository.findAll().stream()
                .sorted(Comparator.comparing(p -> ((com.vpf.entity.CustomerPayment) p).getCreatedDate()).reversed())
                .limit(10)
                .map(customerPaymentService::toResponse)
                .toList();

        return DashboardResponse.builder()
                .todaysOrders(todaysOrders)
                .todaysDeliveries(todaysDeliveries)
                .todaysSales(todaysSales)
                .todaysPurchases(todaysPurchases)
                .customerOutstandingTotal(customerOutstandingTotal)
                .supplierOutstandingTotal(supplierOutstandingTotal)
                .todaysExpenses(todaysExpenses)
                .estimatedProfitLossToday(estimatedProfitToday)
                .pendingOrders(pendingOrders)
                .recentDeliveries(recentDeliveries)
                .recentPayments(recentPayments)
                .build();
    }

    private java.math.BigDecimal nz(java.math.BigDecimal value) {
        return value == null ? java.math.BigDecimal.ZERO : value;
    }
}
