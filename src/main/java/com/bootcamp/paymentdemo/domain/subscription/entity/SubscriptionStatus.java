package com.bootcamp.paymentdemo.domain.subscription.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubscriptionStatus {

    // 구독 상태
    TRIALING("TRIALING", "체험 중"),
    ACTIVE("ACTIVE", "구독 활성"),
    PAST_DUE("PAST_DUE", "결제 연체"),
    CANCELED("CANCELED", "해지됨"),
    ENDED("ENDED", "이용 종료");

    private final String key;
    private final String value;
}
