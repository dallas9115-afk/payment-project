package com.bootcamp.paymentdemo.domain.refund.dto.Response;

import com.bootcamp.paymentdemo.domain.refund.entity.Refund;

import java.time.LocalDateTime;

public record RefundSummaryResponse(
        Long refundId,
        String paymentId,
        String status,
        Long refundAmount,
        String reason,
        LocalDateTime processedAt
) {
    public static RefundSummaryResponse from(Refund refund) {
        return new RefundSummaryResponse(
                refund.getId(),
                refund.getPayment().getPaymentId(),
                refund.getStatus().name(),
                refund.getRefundAmount(),
                refund.getReason(),
                refund.getProcessedAt()
        );
    }
}