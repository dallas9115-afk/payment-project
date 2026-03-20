package com.bootcamp.paymentdemo.domain.point.controller;

import com.bootcamp.paymentdemo.domain.customer.service.MembershipService;
import com.bootcamp.paymentdemo.domain.point.dto.Response.CustomerPointMembershipResponse;
import com.bootcamp.paymentdemo.domain.point.service.PointTransactionService;
import com.bootcamp.paymentdemo.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {

    private final PointTransactionService pointService;
    private final MembershipService membershipService;

    @GetMapping("/{customerId}/me")
    public ResponseEntity<ApiResponse<CustomerPointMembershipResponse>> getCustomerSummary(
            @PathVariable Long customerId,
            Principal principal) {

        // [보안] 호출자와 customerId 일치 검증
        if (principal == null) throw new IllegalArgumentException("인증 실패"); //

        Long loginId = Long.parseLong(principal.getName()); //
        if (!loginId.equals(customerId)) {
            throw new IllegalArgumentException("인증 실패");
        }

        // 각 도메인 서비스에서 데이터 독립적 조회
        Long balance = pointService.getPointBalance(customerId);
        CustomerPointMembershipResponse.MembershipDto membershipDto = membershipService.getMembershipSummary(customerId);

        // 결과 조립
        CustomerPointMembershipResponse response = CustomerPointMembershipResponse.of(customerId, balance, membershipDto);

        return ResponseEntity.ok(ApiResponse.success(response)); //
    }
}
