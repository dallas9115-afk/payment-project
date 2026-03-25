package com.bootcamp.paymentdemo.domain.subscription2.controller;

import com.bootcamp.paymentdemo.config.CustomUser;
import com.bootcamp.paymentdemo.domain.subscription2.dto.request.SubscriptionRequest2;
import com.bootcamp.paymentdemo.domain.subscription2.dto.request.SubscriptionUpdateRequest;
import com.bootcamp.paymentdemo.domain.subscription2.dto.response.BillingHistoryResponse;
import com.bootcamp.paymentdemo.domain.subscription2.dto.response.PlanResponse2;
import com.bootcamp.paymentdemo.domain.subscription2.dto.response.SubscriptionResponse2;
import com.bootcamp.paymentdemo.domain.subscription2.dto.response.SubscriptionStatusResponse;
import com.bootcamp.paymentdemo.domain.subscription2.entity.SubscriptionPlan2;
import com.bootcamp.paymentdemo.domain.subscription2.entity.SubscriptionStatus2;
import com.bootcamp.paymentdemo.domain.subscription2.service.SubscriptionService2;
import com.bootcamp.paymentdemo.global.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/api/subscriptions/v1")
@RequiredArgsConstructor
public class SubscriptionController2 {

    private final SubscriptionService2 subscriptionService;

    /**
     * [POST] 구독 신청
     *
     * @AuthenticationPrincipal을 통해 로그인한 사용자 ID를 가져옵니다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> createSubscription(
            @AuthenticationPrincipal CustomUser user,
            @Valid @RequestBody SubscriptionRequest2 request) {

        Long subId = subscriptionService.initiateSubscription(user.getId(), request.getPlanId(), request);

        // .body() 앞에 형변환 에러가 날 경우 이렇게 작성하세요
        ApiResponse<Map<String, String>> response = ApiResponse.success(Map.of("subscriptionId", String.valueOf(subId)));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * [GET] 구독 상세 조회 (DTO 반환)
     * [피드백 2] 엔티티 직접 반환 ❌ -> DTO 변환 ⭕
     */
    @GetMapping("/{subscriptionId}")
    public ResponseEntity<ApiResponse<SubscriptionResponse2>> getSubscription(
            @AuthenticationPrincipal CustomUser user,
            @PathVariable Long subscriptionId) {

        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.getSubscriptionDto(user.getId(), subscriptionId)
        ));
    }

    /**
     * [PATCH] 구독 해지 (RESTful하게 변경)
     * [피드백 4] POST -> PATCH (상태의 일부를 수정하는 것이므로)
     */
    @PatchMapping("/{subscriptionId}/cancel")
    public ResponseEntity<ApiResponse<SubscriptionStatusResponse>> cancelSubscription(
            @AuthenticationPrincipal CustomUser user,
            @PathVariable Long subscriptionId) {

        log.info("구독 해지 요청: subId={}", subscriptionId);
        subscriptionService.cancelSubscription(user.getId(), subscriptionId);

        return ResponseEntity.ok(ApiResponse.success(
                new SubscriptionStatusResponse(subscriptionId, SubscriptionStatus2.CANCELED)
        ));
    }

    /**
     * [GET] 청구 내역 조회
     * YAML: list-billing-history 매핑
     */
    @GetMapping("/{subscriptionId}/billings")
    public ResponseEntity<ApiResponse<List<BillingHistoryResponse>>> getBillingHistory(
            @AuthenticationPrincipal CustomUser user,
            @PathVariable Long subscriptionId) {
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.getBillingHistoryDto(user.getId(), subscriptionId)
        ));
    }

    /**
     * [GET] 구독 플랜 목록 조회
     * 프론트엔드 plans.html 화면에서 상품 카드들을 보여줄 때 호출합니다.
     */
    /**
     * [GET] 구독 플랜 목록 조회
     * 1. /api/subscriptions/v1/plans (상단 RequestMapping 유지용)
     * 2. /api/plans (프론트엔드 기본 호출 주소 - 절대 경로 매핑)
     */
    @GetMapping({"/plans", "/api/plans"}) // 👈 여기 "/api/plans" 앞에 '/'가 절대 경로 역할을 합니다.
    public ResponseEntity<ApiResponse<List<PlanResponse2>>> getPlans() {
        log.info("구독 플랜 목록 조회 요청 수신 (Mapping: /api/plans)");

        List<SubscriptionPlan2> plans = subscriptionService.getActivePlans();

        List<PlanResponse2> response = plans.stream()
                .map(p -> PlanResponse2.builder()
                        .planId(String.valueOf(p.getId()))
                        .name(p.getName())
                        .amount(p.getPrice())
                        .billingCycle(p.getInterval().name())
                        .description(p.getDescription())
                        .build())
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
