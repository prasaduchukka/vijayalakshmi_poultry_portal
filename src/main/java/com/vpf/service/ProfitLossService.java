package com.vpf.service;

import com.vpf.dto.ProfitLossResponse;
import com.vpf.repository.DeliveryRepository;
import com.vpf.repository.ExpenseRepository;
import com.vpf.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Estimated Profit/Loss = Sales Revenue - Purchase Cost - Recorded Expenses.
 * Deliberately labelled "estimated" per the requirements, since the owner may
 * later add further costs or accounting rules.
 */
@Service
@RequiredArgsConstructor
public class ProfitLossService {

    private final DeliveryRepository deliveryRepository;
    private final PurchaseRepository purchaseRepository;
    private final ExpenseRepository expenseRepository;

    public ProfitLossResponse calculate(LocalDate from, LocalDate to) {
        var sales = deliveryRepository.sumSalesBetween(from, to);
        var purchases = purchaseRepository.sumPurchasesBetween(from, to);
        var expenses = expenseRepository.sumExpensesBetween(from, to);
        sales = sales == null ? java.math.BigDecimal.ZERO : sales;
        purchases = purchases == null ? java.math.BigDecimal.ZERO : purchases;
        expenses = expenses == null ? java.math.BigDecimal.ZERO : expenses;
        var profit = sales.subtract(purchases).subtract(expenses);

        return ProfitLossResponse.builder()
                .fromDate(from)
                .toDate(to)
                .salesRevenue(sales)
                .purchaseCost(purchases)
                .recordedExpenses(expenses)
                .estimatedProfitLoss(profit)
                .build();
    }
}
