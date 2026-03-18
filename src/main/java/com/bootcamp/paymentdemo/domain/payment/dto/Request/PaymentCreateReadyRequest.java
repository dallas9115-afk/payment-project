package com.bootcamp.paymentdemo.domain.payment.dto.Request;

public record PaymentCreateReadyRequest(
        Long orderId,
        Long totalAmount
) {
}
