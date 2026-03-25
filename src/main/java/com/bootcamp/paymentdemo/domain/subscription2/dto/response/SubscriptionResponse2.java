package com.bootcamp.paymentdemo.domain.subscription2.dto.response;

import com.bootcamp.paymentdemo.domain.subscription2.entity.Subscription2;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class SubscriptionResponse2 {
    private String subscriptionId;    // YAML: subscriptionId (String으로 처리하는 경우가 많음)
    private String customerUid;       // YAML: customerUid 추가 필요
    private String planId;            // YAML: planId (플랜의 식별자)
    private String planName;          // 화면 표시용
    private String status;            // YAML: status (ACTIVE, CANCELED 등)
    private Long amount;              // YAML: amount (우리의 price)
    private LocalDateTime currentPeriodEnd; // YAML: currentPeriodEnd (우리 엔티티의 nextBillingDate)
    private String last4;             // 카드 정보

    public static SubscriptionResponse2 fromEntity(Subscription2 entity) {
        return SubscriptionResponse2.builder()
                .subscriptionId(String.valueOf(entity.getId()))
                .customerUid(entity.getCustomer().getEmail()) // 또는 고유 UID
                .planId(String.valueOf(entity.getPlan().getId()))
                .planName(entity.getPlan().getName())
                .status(entity.getStatus().name())
                .amount(entity.getPlan().getPrice())
                .currentPeriodEnd(entity.getNextBillingDate())
                .last4(entity.getPaymentMethod().getLast4())
                .build();
    }
}
