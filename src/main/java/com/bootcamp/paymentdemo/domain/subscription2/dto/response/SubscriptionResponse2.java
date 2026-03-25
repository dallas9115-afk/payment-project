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
    private Long subscriptionId;
    private String planName;
    private String status;
    private Long amount;
    private LocalDateTime nextBillingDate;
    private String last4; // 카드 뒷자리 정보만 노출

    public static SubscriptionResponse2 fromEntity(Subscription2 entity) {
        return SubscriptionResponse2.builder()
                .subscriptionId(entity.getId())
                .planName(entity.getPlan().getName())
                .status(entity.getStatus().name())
                .amount(entity.getPlan().getPrice())
                .nextBillingDate(entity.getNextBillingDate())
                .last4(entity.getPaymentMethod().getLast4())
                .build();
    }

}
