package com.bootcamp.paymentdemo.domain.subscription.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlanStatus {

    ACTIVE("ACTIVE", "활성"),
    INACTIVE("INACTIVE", "비활성");

    private final String key;
    private final String value;
}
