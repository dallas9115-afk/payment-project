package com.bootcamp.paymentdemo.domain.payment.dto.Response;

import com.bootcamp.paymentdemo.domain.payment.entity.Payment;

/**
 * checkout-ready 응답은 프론트에서 실제로 필요한 최소 필드만 유지합니다.
 * - success: 생성 성공 여부
 * - paymentId: PortOne SDK 호출 시 사용할 결제 키
 * - status: 선택 필드 (READY)
 */
public record PaymentCreateReadyResponse(
        boolean success,
        String paymentId,
        String status
) {
    public static PaymentCreateReadyResponse checkoutReady(Payment payment) {
        return new PaymentCreateReadyResponse(
                true,
                payment.getPaymentId(),
                payment.getStatus().name()
        );
    }
}
