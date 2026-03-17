package com.bootcamp.paymentdemo.domain.order.entity;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    PAYMENT_PENDING("PAYMENT_PENDING", "결제 대기"),    // 결제 대기
    ORDER_COMPLETED("ORDER_COMPLETED","주문 완료"),     // 주문 완료
    REFUNDED("REFUNDED","환불");                       // 환불

    private final String key;
    private final String value;
}
