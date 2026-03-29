package com.bootcamp.paymentdemo.domain.subscription.dto.request;

import com.bootcamp.paymentdemo.domain.subscription.enums.SubscriptionAction;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionUpdateRequest {

    @NotNull(message = "액션 타입은 필수입니다.")
    private SubscriptionAction action; // CANCEL 등이 들어옵니다.

    private String reason; // 해지 사유
}
