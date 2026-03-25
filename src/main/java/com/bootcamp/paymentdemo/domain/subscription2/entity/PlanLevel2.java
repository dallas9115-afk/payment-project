package com.bootcamp.paymentdemo.domain.subscription2.entity;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlanLevel2 {

    BASIC("BASIC", "베이직", "매월 기본 과자 상품을 구독할 수 있는 플랜", 10000),
    STANDARD("STANDARD", "스탠다드", "매월 과자 구독과 함께 1+1 혜택 및 새로운 맛 과자를 추가로 받을 수 있는 플랜", 20000),
    VIP("VIP", "VIP", "매월 과자 구독과 함께 1+1 혜택, 새로운 맛 과자 제공, VIP 전용 특별 과자까지 받을 수 있는 플랜", 50000);

    private final String key;
    private final String value;
    private final String content;
    private final int additionalAmount;
}
