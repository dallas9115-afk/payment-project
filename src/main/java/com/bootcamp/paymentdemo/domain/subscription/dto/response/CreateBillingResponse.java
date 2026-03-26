package com.bootcamp.paymentdemo.domain.subscription.dto.response;

import com.bootcamp.paymentdemo.domain.subscription.entity.SubscriptionBilling;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CreateBillingResponse {
    private boolean success;
    private String billingId;
    private String paymentId;
    private Long amount;
    private String status;

    public static CreateBillingResponse fromEntity(SubscriptionBilling billing) {
        return CreateBillingResponse.builder()
                .success(!"FAILED".equalsIgnoreCase(billing.getStatus().name()))
                .billingId(String.valueOf(billing.getId()))
                .paymentId(billing.getPaymentId())
                .amount(billing.getAmount())
                .status(resolveStatus(billing))
                .build();
    }

    private static String resolveStatus(SubscriptionBilling billing) {
        return switch (billing.getStatus()) {
            case SUCCESS -> "COMPLETED";
            case FAILED -> "FAILED";
            case REQUESTED, READY -> "PENDING";
            case CANCELED -> "CANCELED";
        };
    }
}
