package com.bootcamp.paymentdemo.domain.customer.controller;

import com.bootcamp.paymentdemo.domain.customer.dto.request.CustomerLoginRequest;
import com.bootcamp.paymentdemo.domain.customer.dto.request.CustomerSignupRequest;
import com.bootcamp.paymentdemo.domain.customer.dto.response.CustomerLoginResponse;
import com.bootcamp.paymentdemo.domain.customer.dto.response.CustomerSignupResponse;
import com.bootcamp.paymentdemo.domain.customer.dto.response.MembershipGradePolicyResponse;
import com.bootcamp.paymentdemo.domain.customer.service.CustomerService;
import com.bootcamp.paymentdemo.domain.customer.service.MembershipService;
import com.bootcamp.paymentdemo.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customer")
public class CustomerController {

    private final CustomerService customerService;
    private final MembershipService membershipService;

    // 회원가입
    @PostMapping("/v1/customers/signup")
    public ResponseEntity<ApiResponse<CustomerSignupResponse>> signup(
            @Valid @RequestBody CustomerSignupRequest request
            ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(customerService.signUp(request)));
    }

    // 로그인
    @PostMapping("/v1/customers/login")
    public ResponseEntity<ApiResponse<CustomerLoginResponse>> login(
            @Valid @RequestBody CustomerLoginRequest request
            ) {
        CustomerLoginResponse response = customerService.login(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

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
