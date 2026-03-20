package com.bootcamp.paymentdemo.domain.payment.dto.Request;

public record PaymentCreateReadyRequest(
        String orderId,
        Long totalAmount,
        Long pointsToUse
) {
}
