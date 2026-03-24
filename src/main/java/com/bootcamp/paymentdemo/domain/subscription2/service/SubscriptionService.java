package com.bootcamp.paymentdemo.domain.subscription2.service;


import com.bootcamp.paymentdemo.domain.payment.enums.PaymentMethodStatus;
import com.bootcamp.paymentdemo.domain.payment.service.PortOneApiClient;
import com.bootcamp.paymentdemo.domain.subscription2.dto.BillingContext;
import com.bootcamp.paymentdemo.domain.subscription2.dto.request.SubscriptionRequest;
import com.bootcamp.paymentdemo.domain.subscription2.entity.*;
import com.bootcamp.paymentdemo.domain.subscription2.repository.SubscriptionPaymentMethodRepository;
import com.bootcamp.paymentdemo.domain.subscription2.repository.SubscriptionBillingRepository;
import com.bootcamp.paymentdemo.domain.subscription2.repository.SubscriptionPlanRepository;
import com.bootcamp.paymentdemo.domain.subscription2.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

        // 1. [DB 작업] PENDING 저장 및 결제에 필요한 '바구니' 수령 (트랜잭션 1 종료)
        BillingContext context = savePendingSubscription(customerId, planId, request);

        // [체험판 처리]
        // BillingContext에 체험판 여부(isTrial) 정보를 넣었다면 더 깔끔하지만,
        // 일단 현재 구조에서는 planId로 체크하거나 context의 특정 필드로 판단합니다.
        if (context.amount() == 0) { // 예: 체험판은 결제 금액이 0원인 경우
            log.info("체험 기간 구독으로 결제 없이 바로 종료합니다. SubID: {}", context.subscriptionId());
            return context.subscriptionId();
        }

        // 2. [외부 API 호출] 트랜잭션 외부 실행 (DB 조회 없이 context에서 바로 꺼냄!)
        // 💡 피드백 반영: billingRepository.findById 싹 제거!
        String paymentId = "SUB_" + context.billingId() + "_" + UUID.randomUUID().toString().substring(0, 8);

        log.info("포트원 결제 요청 시작: paymentId={}, customerId={}", paymentId, context.customerId());

        boolean isSuccess = portOneApiClient.payWithBillingKey(
                context.billingKey(),      // 바구니에서 꺼냄
                paymentId,
                context.amount().intValue() // 바구니에서 꺼냄
        );

        // 3. [DB 작업] 결과 업데이트 (트랜잭션 2 시작 및 종료)
        updatePaymentResult(context.subscriptionId(), context.billingId(), isSuccess, paymentId);

        return context.subscriptionId();
    }

    @Transactional //이 메서드가 끝나면 DB 커넥션을 즉시 반납
    public BillingContext savePendingSubscription(Long customerId, Long planId, SubscriptionRequest request) {
       try {
           // [멱등성 체크 추가]
           // 오늘 날짜로 이미 'READY'나 'SUCCESS'인 청구가 있는지 확인합니다.
           // (첫 결제는 보통 '오늘'이 결제 예정일이 됩니다)
           LocalDateTime today = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);

           // 만약 이미 진행 중인 건이 있다면 새로 만들지 않고 기존 ID를 돌려주거나 예외를 던집니다.
           // 여기서는 안전하게 예외를 던져서 중복 진행을 막는 방식을 추천해요.
           boolean alreadyExists = billingRepository.existsByCustomerIdAndPlanIdAndScheduledDate(
                   customerId, planId, today);

           if (alreadyExists) {
               log.warn("이미 오늘 해당 플랜에 대한 결제 시도가 있었습니다. customerId={}", customerId);
               throw new IllegalStateException("이미 결제가 진행 중인 요청입니다.");
           }
           // 1. 플랜 조회
           SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                   .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플랜입니다."));

           // 2. 결제 수단(카드 정보) 저장
           // 기존에 설정된 기본 카드가 있다면 해제 (정합성)
           subscriptionPaymentMethodRepository.findByCustomerIdAndIsDefaultTrue(customerId)
                   .ifPresent(PaymentMethod2::unsetDefault);

           PaymentMethod2 method = PaymentMethod2.builder()
                   .customerId(customerId)
                   .billingKey(request.getBillingKey())
                   .customerUid(request.getCustomerUid())
                   .cardBrand(request.getCardBrand())
                   .last4(request.getLast4())
                   .isDefault(true)
                   .status(PaymentMethodStatus.ACTIVE)
                   .build();
           subscriptionPaymentMethodRepository.save(method);

           // 3. 구독 정보 생성 (PENDING 상태)
           Subscription subscription = Subscription.builder()
                   .customerId(customerId)
                   .plan(plan)
                   .paymentMethod(method)
                   .status(SubscriptionStatus.PENDING)
                   .build();

           // [디테일 추가] 체험 기간이 있다면 여기서 바로 TRIALING으로 세팅
           if (plan.getTrialPeriodDays() > 0) {
               subscription.startTrial(plan.getTrialPeriodDays());
           } else {
               // 즉시 결제 대상이면 다음 결제일을 지금으로 설정
               subscription.setNextBillingDate(LocalDateTime.now());
           }

           subscriptionRepository.save(subscription);

           // 4. 구독 청구(Billing) 로그 생성 (READY 상태)
           // 💡 피드백 반영: 중복 결제 방지를 위해 여기서 멱등성 체크를 먼저 해도 좋습니다.
           // 4. Billing 저장
           SubscriptionBilling billing = billingRepository.save(SubscriptionBilling.builder()
                   .subscription(subscription)
                   .amount(plan.getPrice())
                   .scheduledDate(today)
                   .status(BillingStatus.READY)
                   .build());

           // DB에 저장하고 영속화된 객체를 받습니다.
           SubscriptionBilling savedBilling = billingRepository.save(billing);

           // [개선] DB 조회 없이 방금 만든 객체들에서 바로 꺼내서 반환!
           return new BillingContext(
                   savedBilling.getId(),      // 여기서 사용!
                   subscription.getId(),
                   method.getBillingKey(),
                   plan.getPrice(),
                   customerId.toString()
           );
       } catch (DataIntegrityViolationException e) {
           // [2차 방어] DB 유니크 인덱스에 걸렸을 때 (레이스 컨디션 발생 시)
           log.warn("DB 수준에서 중복 결제 시도 차단됨 (Unique Constraint): {}", e.getMessage());
           throw new IllegalStateException("이미 결제가 진행 중인 요청입니다. 잠시 후 다시 시도해주세요.");
       }
    }

    @Transactional // 3번 단계를 위한 짧은 트랜잭션
    public void updatePaymentResult(Long subId, Long billingId, boolean isSuccess, String paymentId) {
        Subscription sub = subscriptionRepository.findById(subId).orElseThrow();
        SubscriptionBilling billing = billingRepository.findById(billingId).orElseThrow();

        if (isSuccess) {
            billing.markRequested(paymentId); // 상태를 REQUESTED로 변경 (웹훅 대기)
        } else {
            sub.toPastDue();         // 미납 처리
            billing.updateStatus(BillingStatus.FAILED); // 실패 기록
        }
    }



    @Transactional
    public void confirmSubscription(String paymentId) {
        // 1. paymentid 수정
        SubscriptionBilling billing = billingRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 결제 건입니다."));

        // 2. 중복 처리 방지
        if (billing.isCompleted()) {
            log.info("이미 처리가 완료된 결제 건입니다. paymentId={}", paymentId);
            return;
        }

        // 3. 결제 로그 업데이트 (입력받은 paymentId를 그대로 전달)
        billing.complete(paymentId);

        // 4. 구독 본체 활성화
        Subscription subscription = billing.getSubscription();
        subscription.activate();

        log.info("구독 최종 활성화 완료: SubID={}, PaymentID={}",
                subscription.getId(), paymentId);
    }

    private Long extractBillingId(String paymentId) {
        log.debug("Billing ID 추출 시도: {}", paymentId);

        String[] parts = paymentId.split("_");
        for (String part : parts) {
            // matches("\\d+")는 해당 문자열이 전부 숫자인지 체크합니다.
            if (part.matches("\\d+")) {
                return Long.parseLong(part);
            }
        }

        log.error("결제 식별자 파싱 실패 - paymentId: {}", paymentId);
        throw new IllegalArgumentException("결제 식별자 형식이 올바르지 않습니다.");
    }


    public void executeRecurringBilling(Subscription sub) {

        // 1. 장부 생성 (트랜잭션 1 시작 및 종료)
        BillingContext context = prepareRecurringBilling(sub);
        if (context == null) return;

        // 2. 외부 API 호출 (트랜잭션 밖 - API가 느려도 DB는 안전!)
        String paymentId = "SUB_RECUR_" + context.billingId() + "_" + UUID.randomUUID().toString().substring(0, 8);

        log.info("정기 결제 요청 시작: paymentId={}", paymentId);

        boolean isSuccess = portOneApiClient.payWithBillingKey(
                context.billingKey(),
                paymentId,
                context.amount().intValue()
        );

        // 3. 결과 업데이트 (트랜잭션 2 시작 및 종료)
        // 아까 만든 updatePaymentResult 메서드를 재사용하면 됩니다!
        updatePaymentResult(context.subscriptionId(), context.billingId(), isSuccess, paymentId);
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


    @Transactional // 정기 결제용 장부.
    public BillingContext prepareRecurringBilling(Subscription sub) {
        // 1. [멱등성 체크] 이번 예정일(nextBillingDate)에 이미 발행된 장부가 있는지 확인
        LocalDateTime targetDate = sub.getNextBillingDate();
        if (billingRepository.existsBySubscriptionAndScheduledDate(sub, targetDate)) {
            log.warn("이미 처리 중인 결제 건입니다. SubID: {}, Date: {}", sub.getId(), targetDate);
            return null; // 이미 있으면 빈 바구니 반납
        }

        // 2. Billing 장부 생성 (READY 상태)
        SubscriptionBilling billing = billingRepository.save(SubscriptionBilling.builder()
                .subscription(sub)
                .amount(sub.getPlan().getPrice())
                .scheduledDate(targetDate)
                .status(BillingStatus.READY)
                .build());

        // 3. 외부 API에 필요한 정보만 바구니(Context)에 담아서 반환
        return new BillingContext(
                billing.getId(),
                sub.getId(),
                sub.getPaymentMethod().getBillingKey(),
                sub.getPlan().getPrice(),
                sub.getCustomerId().toString()
        );
    }

//    // 구독 취소시 사용할 로직 -> 위에 메서드랑 변경할거야요
//@Transactional
//public void cancelSubscription(Long customerId, Long subscriptionId) {
//    Subscription subscription = subscriptionRepository.findById(subscriptionId)
//            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));
//
//    if (!subscription.getCustomerId().equals(customerId)) {
//        throw new IllegalStateException("해지 권한이 없습니다.");
//    }
//
//    try {
//        // [외부 API 호출] 포트원 빌링키 삭제
//        String billingKey = subscription.getPaymentMethod().getBillingKey();
//        portOneApiClient.unsubscribeBillingKey(billingKey);
//
//        // 성공 시 정상 해지 처리
//        subscription.cancel();
//        log.info("구독 해지 완료: SubID={}", subscriptionId);
//
//    } catch (Exception e) {
//        // [피드백 반영] 외부 API 실패 시 상태를 CANCEL_FAILED로 변경
//        // 이렇게 해두면 나중에 관리자 페이지에서 '해지 실패건'만 모아서 재시도할 수 있습니다.
//        subscription.updateStatus(SubscriptionStatus.CANCEL_FAILED);
//
//        log.error("포트원 빌링키 해지 중 오류 발생 - 관리자 확인 필요: SubID={}, Error={}",
//                subscriptionId, e.getMessage());
//
//        // 사용자에게는 일단 알림을 주거나, 내부 정책에 따라 예외를 던질 수도 있습니다.
//        throw new RuntimeException("결제 대행사 해지 통신 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
//    }
//}


//    /**
//     * 포트원 빌링키 삭제(해지) 요청
//     */
//    public void unsubscribeBillingKey(String billingKey) {
//        String url = portOneProperties.getApi().getBaseUrl() + "/billing-keys/" + billingKey;
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Authorization", "PortOne " + portOneProperties.getApi().getSecret());
//
//        HttpEntity<Void> entity = new HttpEntity<>(headers);
//
//        try {
//            restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
//        } catch (Exception e) {
//            log.error("빌링키 삭제 API 호출 실패: {}", e.getMessage());
//            throw new PortOneApiException("빌링키 삭제 중 오류 발생", true);
//        }
//    }




}
