package com.bootcamp.paymentdemo.domain.payment.dto.Response;

import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentStatus;

import java.time.LocalDateTime;

// 주문 조회 화면에서 결제 기준 요약 정보를 붙일 때 사용하는 DTO
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
