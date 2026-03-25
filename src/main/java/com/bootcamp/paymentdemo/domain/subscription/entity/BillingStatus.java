package com.bootcamp.paymentdemo.domain.subscription.entity;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BillingStatus {

    // 결제 상태
    SUCCESS("SUCCESS", "결제 성공"),
    FAILED("FAILED", "결제 실패");

    private final String key;
    private final String value;


}
