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

//수정
@Slf4j
@RestController
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/api/subscriptions/v1")
    public ResponseEntity<Map<String, String>> createSubscription(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SubscriptionRequest request) {

        Long subId = subscriptionService.initiateSubscription(userId, request.getPlanId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("subscriptionId", String.valueOf(subId)));
    }

    @GetMapping("/api/subscriptions/v1/{subscriptionId}")
    public ResponseEntity<SubscriptionResponse> getSubscription(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long subscriptionId) {
        return ResponseEntity.ok(subscriptionService.getSubscriptionDto(userId, subscriptionId));
    }

    @PatchMapping("/api/subscriptions/v1/{subscriptionId}/cancel")
    public ResponseEntity<SubscriptionStatusResponse> cancelSubscription(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long subscriptionId,
            @Valid @RequestBody SubscriptionUpdateRequest request) {

        return ResponseEntity.ok(subscriptionService.updateSubscription(userId, subscriptionId, request));
    }

    @GetMapping("/api/subscriptions/v1/{subscriptionId}/billings")
    public ResponseEntity<List<BillingHistoryResponse>> getBillingHistory(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long subscriptionId) {
        return ResponseEntity.ok(subscriptionService.getBillingHistoryDto(userId, subscriptionId));
    }

    @PostMapping("/api/subscriptions/v1/{subscriptionId}/billings")
    public ResponseEntity<CreateBillingResponse> createBilling(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long subscriptionId,
            @RequestBody(required = false) CreateBillingRequest request) {
        return ResponseEntity.ok(subscriptionService.createBilling(userId, subscriptionId));
    }

    @GetMapping("/api/plans")
    public ResponseEntity<List<PlanResponse>> getPlans() {
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
