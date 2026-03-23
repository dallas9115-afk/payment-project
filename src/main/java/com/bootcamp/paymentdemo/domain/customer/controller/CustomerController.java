package com.bootcamp.paymentdemo.domain.customer.controller;

import com.bootcamp.paymentdemo.domain.customer.dto.response.MembershipGradePolicyResponse;
import com.bootcamp.paymentdemo.domain.customer.service.MembershipService;
import com.bootcamp.paymentdemo.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customer")
public class CustomerController {

    private final MembershipService membershipService;

    //멤버십 정책 조회
    @GetMapping("/v1/membership-policies")
    public ResponseEntity<ApiResponse<List<MembershipGradePolicyResponse>>> getMembershipPolicies() {
        // 서비스의 전체 등급 정책 리스트 반환
        List<MembershipGradePolicyResponse> response = membershipService.getAllMembershipPolicies();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }
}