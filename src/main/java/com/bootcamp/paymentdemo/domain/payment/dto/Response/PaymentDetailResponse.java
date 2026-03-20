package com.bootcamp.paymentdemo.domain.payment.dto.Response;

/**
 * 결제 상세 조회(PortOne 기준) 응답 DTO
 *
 * 다른 도메인(주문 등)에서는 이 DTO만 받아서 사용하면 됩니다.
 */
public record PaymentDetailResponse(
        String paymentId,
        String status,
        String storeId,
        Long totalAmount,
        String failureReason
) {
    public static PaymentDetailResponse from(PortOnePaymentInfoResponse source) {
        return new PaymentDetailResponse(
                source.getPaymentId(),
                source.getStatus(),
                source.getStoreId(),
                source.resolveTotalAmount(),
                source.resolveFailureReason()
        );
    }
}
