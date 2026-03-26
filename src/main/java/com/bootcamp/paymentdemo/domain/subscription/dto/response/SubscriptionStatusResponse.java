package com.bootcamp.paymentdemo.domain.subscription.dto.response;

import com.bootcamp.paymentdemo.domain.subscription.entity.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SubscriptionStatusResponse {
    private Long subscriptionId;
    private SubscriptionStatus status; // Enum 타입을 그대로 사용 (CANCELED, ACTIVE 등)

    // 필요하다면 메시지를 추가할 수도 있습니다.
    private String message;

    public SubscriptionStatusResponse(Long subscriptionId, SubscriptionStatus status) {
        this.subscriptionId = subscriptionId;
        this.status = status;
        this.message = "상태가 " + status.name() + "(으)로 변경되었습니다.";
    }
}