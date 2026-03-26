package com.bootcamp.paymentdemo.domain.subscription.dto.response;

import com.bootcamp.paymentdemo.domain.subscription.entity.SubscriptionBilling;
import com.bootcamp.paymentdemo.domain.subscription.entity.BillingInterval;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BillingHistoryResponse {
    private Long billingId;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private LocalDateTime scheduledDate; // 결제(예정)일
    private LocalDateTime attemptDate;
    private Long amount;                // 결제 금액
    private String status;              // 상태 (SUCCESS, FAILED 등)
    private String paymentId;           // 포트원 결제 번호 (문의용)
    private String failureMessage;

    public static BillingHistoryResponse fromEntity(SubscriptionBilling entity) {
        LocalDateTime periodStart = entity.getScheduledDate();
        BillingInterval interval = entity.getSubscription().getPlan().getInterval();
        LocalDateTime periodEnd = periodStart;

        if (periodStart != null) {
            periodEnd = interval == BillingInterval.YEARLY
                    ? periodStart.plusYears(1)
                    : periodStart.plusMonths(1);
        }

        return BillingHistoryResponse.builder()
                .billingId(entity.getId())
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .scheduledDate(entity.getScheduledDate())
                .attemptDate(entity.getCreatedAt())
                .amount(entity.getAmount())
                .status(resolveStatus(entity))
                .paymentId(entity.getPaymentId())
                .failureMessage(entity.getErrorMessage())
                .build();
    }

    private static String resolveStatus(SubscriptionBilling entity) {
        return switch (entity.getStatus()) {
            case SUCCESS -> "COMPLETED";
            case FAILED -> "FAILED";
            case REQUESTED, READY -> "PENDING";
            case CANCELED -> "CANCELED";
        };
    }
}
