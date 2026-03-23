package com.bootcamp.paymentdemo.domain.payment.dto.Response;

import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentStatus;

import java.time.LocalDateTime;

// 주문조회에서 결제상태를 호출하는 기능이 프론트엔드에없기때문에 사용하진않음
public record PaymentSummaryResponse(
        String paymentId,
        PaymentStatus status,
        Long amount,
        LocalDateTime paidAt,
        LocalDateTime refundedAt
) {
    public static PaymentSummaryResponse from(Payment payment) {
        return new PaymentSummaryResponse(
                payment.getPaymentId(),
                payment.getStatus(),
                payment.getPgAmount(),
                payment.getPaidAt(),
                payment.getRefundedAt()
        );
    }
}
