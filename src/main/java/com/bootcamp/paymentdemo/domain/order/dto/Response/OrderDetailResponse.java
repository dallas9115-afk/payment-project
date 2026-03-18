package com.bootcamp.paymentdemo.domain.order.dto.Response;

import com.bootcamp.paymentdemo.domain.order.entity.OrderItem;
import lombok.Getter;

import java.util.List;

@Getter
public class OrderDetailResponse {

private final String orderNumber;
private final String orderId;
private final Integer totalAmount;
private final Integer finalAmount;
private final String status;
private final List<OrderItem> items;

    public OrderDetailResponse(String orderNumber, String orderId, Integer totalAmount, Integer finalAmount, String status, List<OrderItem> items) {
        this.orderNumber = orderNumber;
        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.finalAmount = finalAmount;
        this.status = status;
        this.items = items;
    }
}
