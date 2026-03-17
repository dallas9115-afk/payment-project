package com.bootcamp.paymentdemo.domain.customer.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Grade {
    NORMAL, // 일반 회원
    VIP,    // VIP 회원
    VVIP    // VVIP 회원
}
