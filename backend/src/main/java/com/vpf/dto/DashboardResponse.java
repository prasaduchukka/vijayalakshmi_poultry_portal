package com.vpf.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardResponse {
    private long todaysOrders;
    private long todaysDeliveries;
    private BigDecimal todaysSales;
    private long todaysPurchases;
    private BigDecimal customerOutstandingTotal;
    private BigDecimal supplierOutstandingTotal;
    private BigDecimal todaysExpenses;
    private BigDecimal estimatedProfitLossToday;
    private List<OrderResponse> pendingOrders;
    private List<DeliveryResponse> recentDeliveries;
    private List<CustomerPaymentResponse> recentPayments;
}
