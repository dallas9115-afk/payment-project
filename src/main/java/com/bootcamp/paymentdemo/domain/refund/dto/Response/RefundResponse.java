package com.bootcamp.paymentdemo.domain.refund.dto.Response;


import com.bootcamp.paymentdemo.domain.refund.entity.Refund;

public record RefundResponse(
        boolean success,
        String orderId,
        String status,
        String paymentId,
        String message
) {
    public static RefundResponse success(Refund refund, String message) {
        return new RefundResponse(
                true,
                refund.getPayment().getOrder().getOrderId(),
                refund.getStatus().name(),
                refund.getPayment().getPaymentId(),
                message
        );
    }

    public static RefundResponse failed(Refund refund, String message) {
        return new RefundResponse(
                false,
                refund.getPayment().getOrder().getOrderId(),
                refund.getStatus().name(),
                refund.getPayment().getPaymentId(),
                message
        );
    }

    public static RefundResponse alreadyRefunded(Refund refund) {
        return new RefundResponse(
                true,
                refund.getPayment().getOrder().getOrderId(),
                refund.getStatus().name(),
                refund.getPayment().getPaymentId(),
                "이미 환불된 결제입니다."
        );
    }
}