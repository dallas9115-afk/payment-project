package com.bootcamp.paymentdemo.domain.subscription.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionRequest {

    @NotNull(message = "플랜 ID는 필수입니다.")
    private Long planId;           // 선택한 요금제 PK

    @NotBlank(message = "빌링키는 필수입니다.")
    private String billingKey;     // 포트원에서 발급받은 열쇠 (가장 중요!)

    @NotBlank(message = "고객 식별자는 필수입니다.")
    private String customerUid;    // 우리가 만든 이름표 (CUST-123 등)

    private Long amount;              // YAML: amount (우리의 price)


}
