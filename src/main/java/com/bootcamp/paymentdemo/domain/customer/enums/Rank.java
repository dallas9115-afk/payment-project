package com.bootcamp.paymentdemo.domain.customer.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Rank {

    NORMAL("일반"),
    VIP("VIP"),
    VVIP("VVIP");

    private final String displayName;
}