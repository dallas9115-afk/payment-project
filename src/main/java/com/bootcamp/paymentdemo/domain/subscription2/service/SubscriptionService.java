package com.bootcamp.paymentdemo.domain.subscription2.service;


import com.bootcamp.paymentdemo.domain.payment.enums.PaymentMethodStatus;
import com.bootcamp.paymentdemo.domain.payment.service.PortOneApiClient;
import com.bootcamp.paymentdemo.domain.subscription2.dto.request.SubscriptionRequest;
import com.bootcamp.paymentdemo.domain.subscription2.entity.*;
import com.bootcamp.paymentdemo.domain.subscription2.repository.SubscriptionPaymentMethodRepository;
import com.bootcamp.paymentdemo.domain.subscription2.repository.SubscriptionBillingRepository;
import com.bootcamp.paymentdemo.domain.subscription2.repository.SubscriptionPlanRepository;
import com.bootcamp.paymentdemo.domain.subscription2.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionPaymentMethodRepository subscriptionPaymentMethodRepository;
    private final SubscriptionBillingRepository billingRepository;
    private final PortOneApiClient portOneApiClient;

    /**
     * [1차 저장] 구독 신청 및 결제 준비
     * 사용자가 결제창에서 빌링키를 받아온 직후 호출됩니다.
     */
    public Long initiateSubscription(Long customerId, Long planId, SubscriptionRequest request) {

        // 1. 플랜 조회 (어떤 상품을 구독하려나?)
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플랜입니다."));

        // 2. 결제 수단(열쇠) 저장
        // 기존 기본 결제수단이 있다면 해제하고 새로 등록합니다.
        subscriptionPaymentMethodRepository.findByCustomerIdAndIsDefaultTrue(customerId)
                .ifPresent(PaymentMethod::unsetDefault);

        PaymentMethod method = PaymentMethod.builder()
                .customerId(customerId)
                .billingKey(request.getBillingKey())
                .customerUid(request.getCustomerUid())
                .cardBrand(request.getCardBrand())
                .last4(request.getLast4())
                .isDefault(true)
                .status(PaymentMethodStatus.ACTIVE)
                .build();
        subscriptionPaymentMethodRepository.save(method);


        // 3. 구독 정보 생성 (상태는 PENDING - 아직 돈 안 들어옴)
        Subscription subscription = Subscription.builder()
                .customerId(customerId)
                .plan(plan)
                .paymentMethod(method)
                .status(SubscriptionStatus.PENDING) // 👈 여기서 '정합성' 시작!
                .nextBillingDate(LocalDateTime.now()) // 첫 결제는 즉시 시도할 것이므로 현재 시간
                .build();

        // 3-1. 체험판일 경우 분기 추가
        if (plan.getTrialPeriodDays() > 0) {
            // [체험판인 경우]
            subscription.startTrial(plan.getTrialPeriodDays());
            subscriptionRepository.save(subscription);

            log.info("체험 구독 생성 완료: SubID={}, 종료일={}",
                    subscription.getId(), subscription.getTrialEndDate());
            return subscription.getId(); // 즉시 결제 없이 종료
        }

        // 3-2. 멱등성 추가
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0);
        if (billingRepository.existsBySubscriptionAndStatusInAndCreatedAtAfter(
                subscription, List.of(BillingStatus.READY, BillingStatus.SUCCESS), startOfMonth)) {
            log.warn("이미 결제가 진행 중이거나 완료된 구독 신청입니다.");
            return subscription.getId();
        }

        subscriptionRepository.save(subscription);

        // 4. 결제 시도 기록 (Billing 로그 생성)
        // 결제 요청을 보내기 직전에 'READY' 상태로 로그를 남깁니다.
        SubscriptionBilling billing = SubscriptionBilling.builder()
                .subscription(subscription)
                .amount(plan.getPrice())
                .status(BillingStatus.READY) // 결제 준비 상태 기록
                .build();
        billingRepository.save(billing);

        log.info("구독 신청 접수 완료 (PENDING): Subscription ID = {}", subscription.getId());

        // 5. 실제 포트원에 결제 요청 보내기 (빌링키 사용)
        // paymentId는 중복되지 않게 생성해야 합니다 (예: SUB_결제로그ID)
        String paymentId = "SUB_" + billing.getId() + "_" + UUID.randomUUID().toString().substring(0, 8);

        log.info("포트원 정기 결제 요청 시작: paymentId={}, amount={}", paymentId, plan.getPrice());

        // PortOneApiClient의 메서드 호출
        boolean isSuccess = portOneApiClient.payWithBillingKey(
                method.getBillingKey(),
                paymentId,
                plan.getPrice().intValue()
        );

        // 6. 결과에 따른 1차 처리 (성공 시 일단 기록, 최종 확정은 웹훅에서!)
        if (isSuccess) {
            log.info("포트원 결제 요청 송신 성공: paymentId={}", paymentId);
            // 비즈니스 로직에 따라 여기서 바로 ACTIVE로 바꿀 수도 있지만,
            // '정합성'을 위해 웹훅을 기다리거나, 여기서 SUCCESS로 일단 바꿉니다.
        } else {
            log.error("포트원 결제 요청 실패: paymentId={}", paymentId);
            subscription.updateStatus(SubscriptionStatus.PAST_DUE); // 미납 상태로 변경
            billing.updateStatus(BillingStatus.FAILED);           // 로그 실패 기록
        }


        return subscription.getId();
    }



    @Transactional
    public void confirmSubscription(String portOnePaymentId) {
        // 1. portOnePaymentId에서 Billing ID 추출
        // 아까 생성한 형식: "SUB_10_uuid..." -> index 1번이 ID
        Long billingId = extractBillingId(portOnePaymentId);

        SubscriptionBilling billing = billingRepository.findById(billingId)
                .orElseThrow(() -> new IllegalStateException("해당 결제 로그를 찾을 수 없습니다."));

        // 2. 멱등성 체크 (이미 성공했으면 통과)
        if (billing.getStatus() == BillingStatus.SUCCESS) {
            log.info("이미 처리된 결제입니다. paymentId={}", portOnePaymentId);
            return;
        }

        // 3. 결제 로그 업데이트
        billing.complete(portOnePaymentId); // 상태 SUCCESS로 변경 및 ID 저장

        // 4. 구독 본체 활성화
        Subscription subscription = billing.getSubscription();
        subscription.activate(); // 상태 ACTIVE 변경 및 차기 결제일 갱신

        log.info("구독 최종 활성화 완료: SubID={}, PaymentID={}",
                subscription.getId(), portOnePaymentId);
    }

    private Long extractBillingId(String paymentId) {
        try {
            // "SUB_10_abcd" 형태에서 10을 가져옴
            return Long.parseLong(paymentId.split("_")[1]);
        } catch (Exception e) {
            throw new IllegalArgumentException("잘못된 결제 식별자 형식입니다: " + paymentId);
        }
    }


    @Transactional
    public void executeRecurringBilling(Subscription sub) {
        // 멱등성 추가
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0);
        if (billingRepository.existsBySubscriptionAndStatusInAndCreatedAtAfter(
                sub, List.of(BillingStatus.READY, BillingStatus.SUCCESS), startOfMonth)) {
            log.warn("이미 이번 달 정기 결제가 처리되었습니다. Subscription ID: {}", sub.getId());
            return;
        }

        // 1. 새로운 결제 로그 생성 (READY)
        SubscriptionBilling billing = SubscriptionBilling.builder()
                .subscription(sub)
                .amount(sub.getPlan().getPrice())
                .status(BillingStatus.READY)
                .build();
        billingRepository.save(billing);

        // 2. 포트원 빌링키 결제 요청
        String paymentId = "SUB_RECUR_" + billing.getId() + "_" + UUID.randomUUID().toString().substring(0, 8);

        boolean isSuccess = portOneApiClient.payWithBillingKey(
                sub.getPaymentMethod().getBillingKey(),
                paymentId,
                sub.getPlan().getPrice().intValue()
        );

        // 3. 결과 처리
        if (isSuccess) {
            // 성공 시 웹훅이 오겠지만, 스케줄러에서는 안전하게 여기서 바로 처리하거나
            // 웹훅을 기다리도록 설계할 수 있습니다.
            // 여기서는 웹훅이 올 것이라 믿고 로그만 남깁니다.
            log.info("정기 결제 요청 성공: {}", paymentId);
        } else {

            sub.toPastDue(); // 상태를 ACTIVE에서 PAST_DUE로 변경하여 서비스 차단
            billing.fail("정기 결제 실패 (잔액 부족 등)");
            log.warn("정기 결제 요청 실패: {}, 사용자: {}", paymentId, sub.getCustomerId());
        }
    }


    @Transactional
    public void cancelSubscription(Long customerId, Long subscriptionId) {
        // 1. 해당 사용자의 활성화된 구독 찾기
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        // 2. 권한 체크 (내 구독이 맞는지)
        if (!subscription.getCustomerId().equals(customerId)) {
            throw new IllegalStateException("해지 권한이 없습니다.");
        }

        // 3. 해지 처리
        subscription.cancel();

        log.info("구독 해지 완료: CustomerID={}, SubID={}", customerId, subscriptionId);
    }




}
