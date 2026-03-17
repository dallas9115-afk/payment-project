package com.bootcamp.paymentdemo.domain.product.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductStatus {
    SALE("SALE","판매중"),       // 판매중
    SOLD_OUT("SOLD_OUT","품절"),   // 품절
    STOP("STOP","판매 중지");        // 판매중지

    private final String key;
    private final String value;
}
