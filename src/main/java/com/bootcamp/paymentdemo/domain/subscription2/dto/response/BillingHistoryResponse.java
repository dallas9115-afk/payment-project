package com.bootcamp.paymentdemo.domain.subscription2.dto.response;

import com.bootcamp.paymentdemo.domain.subscription2.entity.SubscriptionBilling2;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BillingHistoryResponse {
    private Long billingId;
    private LocalDateTime scheduledDate; // 결제(예정)일
    private Long amount;                // 결제 금액
    private String status;              // 상태 (SUCCESS, FAILED 등)
    private String paymentId;           // 포트원 결제 번호 (문의용)

    public static BillingHistoryResponse fromEntity(SubscriptionBilling2 entity) {
        return BillingHistoryResponse.builder()
                .billingId(entity.getId())
                .scheduledDate(entity.getScheduledDate())
                .amount(entity.getAmount())
                .status(entity.getStatus().name())
                .paymentId(entity.getPaymentId())
                .build();
    }



}
