package com.bootcamp.paymentdemo.domain.order.dto.Response;


import lombok.Getter;

@Getter
public class CreateOrderResponse {

    private final String orderId;
    private final Integer totalAmount;
    private final String orderNumber;

    public CreateOrderResponse(String orderId, Integer totalAmount, String orderNumber) {
        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.orderNumber = orderNumber;
    }
}
