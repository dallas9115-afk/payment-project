package com.bootcamp.paymentdemo.domain.subscription.controller;

import com.bootcamp.paymentdemo.config.CustomUser;
import com.bootcamp.paymentdemo.domain.subscription.dto.request.CreateBillingRequest;
import com.bootcamp.paymentdemo.domain.subscription.dto.request.SubscriptionRequest;
import com.bootcamp.paymentdemo.domain.subscription.dto.request.SubscriptionUpdateRequest;
import com.bootcamp.paymentdemo.domain.subscription.dto.response.BillingHistoryResponse;
import com.bootcamp.paymentdemo.domain.subscription.dto.response.CreateBillingResponse;
import com.bootcamp.paymentdemo.domain.subscription.dto.response.PlanResponse;
import com.bootcamp.paymentdemo.domain.subscription.dto.response.SubscriptionResponse;
import com.bootcamp.paymentdemo.domain.subscription.dto.response.SubscriptionStatusResponse;
import com.bootcamp.paymentdemo.domain.subscription.entity.SubscriptionPlan;
import com.bootcamp.paymentdemo.domain.subscription.entity.SubscriptionStatus;
import com.bootcamp.paymentdemo.domain.subscription.service.SubscriptionService;
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
//@RequestMapping("/api/subscriptions/v1")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * [POST] 구독 신청
     *
     * @AuthenticationPrincipal을 통해 로그인한 사용자 ID를 가져옵니다.
     */
    @PostMapping("/api/subscriptions/v1")
    public ResponseEntity<Map<String, String>> createSubscription(
            @AuthenticationPrincipal CustomUser user,
            @Valid @RequestBody SubscriptionRequest request) {

        Long subId = subscriptionService.initiateSubscription(user.getId(), request.getPlanId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("subscriptionId", String.valueOf(subId)));
    }

    /**
     * [GET] 구독 상세 조회 (DTO 반환)
     * [피드백 2] 엔티티 직접 반환 ❌ -> DTO 변환 ⭕
     */
    @GetMapping("/api/subscriptions/v1/{subscriptionId}")
    public ResponseEntity<SubscriptionResponse> getSubscription(
            @AuthenticationPrincipal CustomUser user,
            @PathVariable Long subscriptionId) {
        return ResponseEntity.ok(subscriptionService.getSubscriptionDto(user.getId(), subscriptionId));
    }

    /**
     * [PATCH] 구독 해지 (RESTful하게 변경)
     * [피드백 4] POST -> PATCH (상태의 일부를 수정하는 것이므로)
     */
    @PatchMapping("/api/subscriptions/v1/{subscriptionId}/cancel")
    public ResponseEntity<SubscriptionStatusResponse> cancelSubscription(
            @AuthenticationPrincipal CustomUser user,
            @PathVariable Long subscriptionId,
            @Valid @RequestBody SubscriptionUpdateRequest request) {

        log.info("구독 해지 요청: subId={}", subscriptionId);
        return ResponseEntity.ok(subscriptionService.updateSubscription(user.getId(), subscriptionId, request));
    }

    /**
     * [GET] 청구 내역 조회
     * YAML: list-billing-history 매핑
     */
    @GetMapping("/api/subscriptions/v1/{subscriptionId}/billings")
    public ResponseEntity<List<BillingHistoryResponse>> getBillingHistory(
            @AuthenticationPrincipal CustomUser user,
            @PathVariable Long subscriptionId) {
        return ResponseEntity.ok(subscriptionService.getBillingHistoryDto(user.getId(), subscriptionId));
    }

    @PostMapping("/api/subscriptions/v1/{subscriptionId}/billings")
    public ResponseEntity<CreateBillingResponse> createBilling(
            @AuthenticationPrincipal CustomUser user,
            @PathVariable Long subscriptionId,
            @RequestBody(required = false) CreateBillingRequest request) {
        return ResponseEntity.ok(subscriptionService.createBilling(user.getId(), subscriptionId));
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
    @GetMapping( "/api/plans") // 👈 여기 "/api/plans" 앞에 '/'가 절대 경로 역할을 합니다.
    public ResponseEntity<List<PlanResponse>> getPlans() {
        log.info("구독 플랜 목록 조회 요청 수신 (Mapping: /api/plans)");

        List<SubscriptionPlan> plans = subscriptionService.getActivePlans();

        List<PlanResponse> response = plans.stream()
                .map(p -> PlanResponse.builder()
                        .planId(String.valueOf(p.getId()))
                        .name(p.getName())
                        .amount(p.getPrice())
                        .billingCycle(p.getInterval().name())
                        .description(p.getDescription())
                        .content(p.getContent())
                        .build())
                .toList();

        return ResponseEntity.ok(response);
    }
}
