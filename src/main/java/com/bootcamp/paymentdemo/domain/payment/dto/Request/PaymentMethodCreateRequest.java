package com.bootcamp.paymentdemo.domain.payment.dto.Request;

import com.bootcamp.paymentdemo.domain.payment.enums.PgProvider;

public record PaymentMethodCreateRequest(
        String billingKey,
        String customerUid,
        PgProvider provider,
        boolean isDefault
) {
}
