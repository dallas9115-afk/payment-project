package com.bootcamp.paymentdemo.domain.payment.dto.Response;

import com.bootcamp.paymentdemo.domain.payment.entity.Payment;

/**
 * 결제 확정 API 응답 DTO
 *
 * 프론트엔드가 주로 확인하는 값은 success, orderId, status 입니다.
 * - success: 결제 확정 처리 성공/실패
 * - orderId: 어떤 주문의 결제인지 식별
 * - status: 현재 결제 상태 (READY / PAID / FAILED / REFUNDED)
 * - message: 사람이 읽기 쉬운 결과 설명
 */
public record PaymentConfirmResponse(
        boolean success,
        Long orderId,
        String status,
        String paymentId,
        String message
) {
    public static PaymentConfirmResponse success(Payment payment) {
        return new PaymentConfirmResponse(
                true,
                payment.getOrder().getId(),
                payment.getStatus().name(),
                payment.getPaymentId(),
                "결제 확정 성공"
        );
    }

    public static PaymentConfirmResponse alreadyProcessed(Payment payment) {
        return new PaymentConfirmResponse(
                true,
                payment.getOrder().getId(),
                payment.getStatus().name(),
                payment.getPaymentId(),
                "이미 처리된 결제입니다."
        );
    }

    public static PaymentConfirmResponse failed(Payment payment, String message) {
        return new PaymentConfirmResponse(
                false,
                payment.getOrder().getId(),
                payment.getStatus().name(),
                payment.getPaymentId(),
                message
        );
    }
}
