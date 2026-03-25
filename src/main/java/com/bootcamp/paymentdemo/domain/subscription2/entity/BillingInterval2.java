package com.bootcamp.paymentdemo.domain.subscription2.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BillingInterval2 {

    MONTHLY("MONTHLY", "월간"),
    YEARLY("YEARLY", "연간");

    private final String key;
    private final String value;
}
