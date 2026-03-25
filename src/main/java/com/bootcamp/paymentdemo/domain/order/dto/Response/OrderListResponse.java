package com.bootcamp.paymentdemo.domain.order.dto.Response;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class OrderListResponse {

    private final String orderNumber;
    private final String orderId;
    private final Integer totalAmount;
    private final Integer usedPoints;
    private final Integer finalAmount;
    private final Integer earnedPoints;
    private final String currency;

    private final String status;
    private final LocalDateTime createdAt;

    public OrderListResponse(String orderNumber, String orderId, Integer totalAmount, Integer finalAmount, String currency, String status, LocalDateTime createdAt) {
        this.orderNumber = orderNumber;
        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.finalAmount = finalAmount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
        this.usedPoints = 0;
        this.earnedPoints = 0;
    }
}
