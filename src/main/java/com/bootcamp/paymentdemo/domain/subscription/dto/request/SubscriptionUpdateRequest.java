package com.bootcamp.paymentdemo.domain.subscription.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Getter // 👈 여기서 getAction() 메서드를 자동으로 만들어줍니다!
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionUpdateRequest {

    @NotBlank(message = "액션 타입은 필수입니다.")
    private String action; // 'cancel' 등이 들어옵니다.

    private String reason; // 해지 사유
}
