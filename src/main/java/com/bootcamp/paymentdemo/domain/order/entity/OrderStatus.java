package com.bootcamp.paymentdemo.domain.order.entity;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    // 상태
    PENDING("PENDING", "결제 대기"),    // 결제 대기
    PAID("PAID","주문 완료"),     // 주문 완료
    CANCELLED("CANCELLED","주문 취소");                       // 환불

    private final String key;
    private final String value;
}
