package com.bootcamp.paymentdemo.domain.customer.dto.response;

import com.bootcamp.paymentdemo.domain.customer.entity.MembershipGradePolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MembershipGradePolicyResponse {
    private String gradeCode;   // NORMAL, VIP, VVIP
    private String gradeName;   // 일반, 우수, 최우수
    private Long minPaidAmount; // 최소 기준 금액
    private Double pointRate;   // 적립률 (0.05 = 5%)

    public static MembershipGradePolicyResponse from(MembershipGradePolicy policy) {
        return MembershipGradePolicyResponse.builder()
                .gradeCode(policy.getGradeCode())
                .gradeName(policy.getGradeName())
                .minPaidAmount(policy.getMinPaidAmount())
                .pointRate(policy.getPointRate())
                .build();
    }
}
