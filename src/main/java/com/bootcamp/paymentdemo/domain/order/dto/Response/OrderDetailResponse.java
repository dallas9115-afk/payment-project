package com.bootcamp.paymentdemo.domain.order.dto.Response;

import com.bootcamp.paymentdemo.domain.order.entity.OrderItem;
import lombok.Getter;

import java.util.List;

@Getter
public class OrderDetailResponse {


    private final String orderNumber;
    private final String orderId;
    private final  Long productId;
    private final String productName;
    private final Integer price;
    private final Integer quantity;
    private final Integer itemTotalAmount;


    public OrderDetailResponse(String orderNumber, String orderId, Long productId, String productName, Integer price, Integer quantity, Integer itemTotalAmount) {
        this.orderNumber = orderNumber;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
        this.itemTotalAmount = itemTotalAmount;
    }
}
