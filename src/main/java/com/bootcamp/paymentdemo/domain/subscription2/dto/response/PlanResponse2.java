package com.bootcamp.paymentdemo.domain.subscription2.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanResponse2 {
    private String planId;      // YAML의 planId: "1", "2" 등 (String 권장)
    private String name;        // 플랜 이름: "베이직", "스탠다드"
    private Long amount;        // YAML의 amount: 가격 (우리 엔티티의 price)
    private String billingCycle; // YAML의 billingCycle: "MONTHLY" (우리 엔티티의 interval)
    private String description; // 플랜 상세 설명
}
