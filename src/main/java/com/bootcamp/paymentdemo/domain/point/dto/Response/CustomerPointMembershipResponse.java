package com.bootcamp.paymentdemo.domain.point.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CustomerPointMembershipResponse {
    private Long customerId; // userId에서 변경
    private Long balance;
    private MembershipDto membership;

    @Getter
    @Builder
    public static class MembershipDto {
        private String grade;
        private Double benefitRate;
        private Long accumulatedAmount;
    }

    public static CustomerPointMembershipResponse of(Long customerId, Long balance, MembershipDto membershipDto) {
        return CustomerPointMembershipResponse.builder()
                .customerId(customerId)
                .balance(balance)
                .membership(membershipDto)
                .build();
    }
}
