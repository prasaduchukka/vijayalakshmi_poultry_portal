package com.vpf.service;

import com.vpf.dto.OrderRequest;
import com.vpf.dto.OrderResponse;
import com.vpf.entity.Customer;
import com.vpf.entity.CustomerOrder;
import com.vpf.exception.ResourceNotFoundException;
import com.vpf.repository.CustomerOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final CustomerOrderRepository orderRepository;
    private final CustomerService customerService;

    public OrderResponse create(OrderRequest req) {
        Customer customer = customerService.getOrThrow(req.getCustomerId());
        CustomerOrder order = new CustomerOrder();
        order.setCustomer(customer);
        apply(order, req);
        orderRepository.save(order);
        return toResponse(order);
    }

    public OrderResponse update(Long id, OrderRequest req) {
        CustomerOrder order = getOrThrow(id);
        if (!order.getCustomer().getId().equals(req.getCustomerId())) {
            order.setCustomer(customerService.getOrThrow(req.getCustomerId()));
        }
        apply(order, req);
        orderRepository.save(order);
        return toResponse(order);
    }

    public List<OrderResponse> findAll() {
        return orderRepository.findAllByOrderByOrderDateDesc().stream().map(this::toResponse).toList();
    }

    public List<OrderResponse> findByCustomer(Long customerId) {
        return orderRepository.findByCustomerIdOrderByOrderDateDesc(customerId).stream().map(this::toResponse).toList();
    }

    public List<OrderResponse> findPending() {
        return orderRepository.findAllByOrderByOrderDateDesc().stream()
                .filter(o -> o.getStatus() == com.vpf.entity.enums.OrderStatus.PENDING)
                .map(this::toResponse).toList();
    }

    public CustomerOrder getOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    private void apply(CustomerOrder order, OrderRequest req) {
        order.setOrderDate(req.getOrderDate());
        order.setNumberOfBoxes(req.getNumberOfBoxes());
        order.setRequestedDeliveryDate(req.getRequestedDeliveryDate());
        order.setStatus(req.getStatus());
        order.setNotes(req.getNotes());
        if (order.getCreatedBy() == null) {
            order.setCreatedBy(req.getCreatedBy());
        }
    }

    private OrderResponse toResponse(CustomerOrder o) {
        return OrderResponse.builder()
                .id(o.getId())
                .customerId(o.getCustomer().getId())
                .customerName(o.getCustomer().getChickenCenterName())
                .orderDate(o.getOrderDate())
                .numberOfBoxes(o.getNumberOfBoxes())
                .requestedDeliveryDate(o.getRequestedDeliveryDate())
                .status(o.getStatus())
                .notes(o.getNotes())
                .createdBy(o.getCreatedBy())
                .createdDate(o.getCreatedDate())
                .build();
    }
}
