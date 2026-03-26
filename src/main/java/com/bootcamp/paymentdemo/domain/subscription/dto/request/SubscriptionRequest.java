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

    // 카드 정보 (사용자에게 보여주거나 나중에 관리하기 용이함)
    private String cardBrand;      // 예: 현대카드, 신한카드
    private String last4;          // 카드 번호 마지막 4자리 (예: 1234)

    // 추가로 필요한 정보가 있다면 여기에 더 넣습니다.
    private String cardType;       // 개인/법인 등 (선택사항)
}
