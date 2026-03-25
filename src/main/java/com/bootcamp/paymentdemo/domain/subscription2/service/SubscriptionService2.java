package com.bootcamp.paymentdemo.domain.subscription2.service;


import com.bootcamp.paymentdemo.domain.payment.enums.PaymentMethodStatus;
import com.bootcamp.paymentdemo.domain.payment.service.PortOneApiClient;
import com.bootcamp.paymentdemo.domain.subscription2.dto.BillingContext2;
import com.bootcamp.paymentdemo.domain.subscription2.dto.request.SubscriptionRequest2;
import com.bootcamp.paymentdemo.domain.subscription2.dto.response.BillingHistoryResponse;
import com.bootcamp.paymentdemo.domain.subscription2.dto.response.SubscriptionResponse2;
import com.bootcamp.paymentdemo.domain.subscription2.entity.*;
import com.bootcamp.paymentdemo.domain.subscription2.repository.SubscriptionPaymentMethodRepository2;
import com.bootcamp.paymentdemo.domain.subscription2.repository.SubscriptionBillingRepository2;
import com.bootcamp.paymentdemo.domain.subscription2.repository.SubscriptionPlanRepository2;
import com.bootcamp.paymentdemo.domain.subscription2.repository.SubscriptionRepository2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService2 {

    private final SubscriptionRepository2 subscriptionRepository;
    private final SubscriptionPlanRepository2 subscriptionPlanRepository;
    private final SubscriptionPaymentMethodRepository2 subscriptionPaymentMethodRepository;
    private final SubscriptionBillingRepository2 billingRepository;
    private final PortOneApiClient portOneApiClient;

    //구독 신청 및 결제 준비
    public Long initiateSubscription(Long customerId, Long planId, SubscriptionRequest2 request) {

        // 1. [DB 작업] PENDING 저장 및 결제에 필요한 '바구니' 수령 (트랜잭션 1 종료)
        BillingContext2 context = savePendingSubscription(customerId, planId, request);

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
    public BillingContext2 savePendingSubscription(Long customerId, Long planId, SubscriptionRequest2 request) {
       try {
           // [멱등성 체크 추가]
           // 오늘 날짜로 이미 'READY'나 'SUCCESS'인 청구가 있는지 확인합니다.
           // (첫 결제는 보통 '오늘'이 결제 예정일이 됩니다)
           LocalDateTime today = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);

           // 만약 이미 진행 중인 건이 있다면 새로 만들지 않고 기존 ID를 돌려주거나 예외를 던집니다.
           // 여기서는 안전하게 예외를 던져서 중복 진행을 막는 방식을 추천해요.
           boolean alreadyExists = billingRepository.existsByCustomerAndPlanAndDate(
                   customerId, planId, today);

           if (alreadyExists) {
               log.warn("이미 오늘 해당 플랜에 대한 결제 시도가 있었습니다. customerId={}", customerId);
               throw new IllegalStateException("이미 결제가 진행 중인 요청입니다.");
           }
           // 1. 플랜 조회
           SubscriptionPlan2 plan = subscriptionPlanRepository.findById(planId)
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
           Subscription2 subscription = Subscription2.builder()
                   .customerId(customerId)
                   .plan(plan)
                   .paymentMethod(method)
                   .status(SubscriptionStatus2.PENDING)
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
           SubscriptionBilling2 savedBilling = billingRepository.save(
                   SubscriptionBilling2.builder()
                           .subscription(subscription)
                           .amount(plan.getPrice())
                           .scheduledDate(today)
                           .status(BillingStatus2.READY)
                           .build());


           // [개선] DB 조회 없이 방금 만든 객체들에서 바로 꺼내서 반환!
           return new BillingContext2(
                   savedBilling.getId(),      // 여기서 사용!
                   subscription.getId(),
                   method.getBillingKey(),
                   plan.getPrice(),
                   customerId
           );
       } catch (DataIntegrityViolationException e) {
           // [2차 방어] DB 유니크 인덱스에 걸렸을 때 (레이스 컨디션 발생 시)
           log.warn("DB 수준에서 중복 결제 시도 차단됨 (Unique Constraint): {}", e.getMessage());
           throw new IllegalStateException("이미 결제가 진행 중인 요청입니다. 잠시 후 다시 시도해주세요.");
       }
    }

    @Transactional // 3번 단계를 위한 짧은 트랜잭션
    public void updatePaymentResult(Long subId, Long billingId, boolean isSuccess, String paymentId) {
        Subscription2 sub = subscriptionRepository.findById(subId)
                .orElseThrow(()-> new IllegalArgumentException("구독 정보를 찾을 수 없습니다."));
        SubscriptionBilling2 billing = billingRepository.findById(billingId)
                .orElseThrow(()-> new IllegalArgumentException("결제 로그를 찾을 수 없습니다."));

        if (isSuccess) {
            billing.markRequested(paymentId); // 상태를 REQUESTED로 변경 (웹훅 대기)
        } else {
            billing.updateStatus(BillingStatus2.FAILED);
            billing.setPaymentId(paymentId);
            sub.toPastDue();         // 미납 처리

            log.warn("결제 요청 실패: billingId={}, paymentId={}", billingId, paymentId);
        }
    }



    @Transactional
    public void confirmSubscription(String paymentId) {
        // 1. paymentid 수정
        SubscriptionBilling2 billing = billingRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 결제 건입니다."));

        // 2. [피드백 반영] 결제 ID 무결성 검증 (보안 강화)
        // DB에 저장된 ID와 실제 웹훅으로 들어온 ID가 같은지 한 번 더 확인합니다.
        if (!billing.getPaymentId().equals(paymentId)) {
            log.error("결제 ID 불일치 보안 위협 감지! DB: {}, Webhook: {}",
                    billing.getPaymentId(), paymentId);
            throw new IllegalStateException("잘못된 결제 식별자 요청입니다.");
        }

        // 3. 중복 처리 방지
        if (billing.isCompleted()) {
            log.info("이미 처리가 완료된 결제 건입니다. paymentId={}", paymentId);
            return;
        }

        // 4. 결제 로그 업데이트 (입력받은 paymentId를 그대로 전달)
        billing.complete(paymentId);

        // 5. 구독 본체 활성화
        Subscription2 subscription = billing.getSubscription();
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


    @Transactional
    public void executeRecurringBilling(Long subId, String paymentId) { // [피드백 3] 엔티티 대신 ID 전달
        // 1. 최신 상태 조회를 통해 영속성 컨텍스트 및 Dirty Checking 보장
        Subscription2 sub = subscriptionRepository.findById(subId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        // [피드백 1] 실행 시점 중복 체크 (조회 시점과 실행 시점 사이의 간극 방어)
        // UNIQUE 제약 조건(subscription_id, scheduled_date)과 함께 사용하면 완벽합니다.
        LocalDateTime targetDate = sub.getNextBillingDate();
        if (billingRepository.existsBySubscriptionAndScheduledDate(sub, targetDate)) {
            log.warn("이미 처리 중이거나 완료된 결제 건입니다. Skip 처리: SubID={}, Date={}", subId, targetDate);
            return;
        }

        if (sub.getStatus() != SubscriptionStatus2.ACTIVE) return;

        // 2. 장부 생성 (기존 로직)
        BillingContext2 context = prepareRecurringBilling(sub);
        if (context == null) return;

        // 3. 외부 API 호출 (전달받은 paymentId 사용)
        log.info("정기 결제 API 호출 시작: SubID={}, PaymentID={}", subId, paymentId);

        boolean isSuccess = portOneApiClient.payWithBillingKey(
                context.billingKey(),
                paymentId,
                context.amount().intValue()
        );

        // 4. 결과 업데이트
        updatePaymentResult(sub.getId(), context.billingId(), isSuccess, paymentId);
    }


    @Transactional // 정기 결제용 장부.
    public BillingContext2 prepareRecurringBilling(Subscription2 sub) {
        // 1. [멱등성 체크] 이번 예정일(nextBillingDate)에 이미 발행된 장부가 있는지 확인
        LocalDateTime targetDate = sub.getNextBillingDate();
        if (billingRepository.existsBySubscriptionAndScheduledDate(sub, targetDate)) {
            log.warn("이미 처리 중인 결제 건입니다. SubID: {}, Date: {}", sub.getId(), targetDate);
            return null; // 이미 있으면 빈 바구니 반납
        }

        // 2. Billing 장부 생성 (READY 상태)
        SubscriptionBilling2 billing = billingRepository.save(SubscriptionBilling2.builder()
                .subscription(sub)
                .amount(sub.getPlan().getPrice())
                .scheduledDate(targetDate)
                .status(BillingStatus2.READY)
                .build());

        // 3. 외부 API에 필요한 정보만 바구니(Context)에 담아서 반환
        return new BillingContext2(
                billing.getId(),
                sub.getId(),
                sub.getPaymentMethod().getBillingKey(),
                sub.getPlan().getPrice(),
                sub.getCustomerId()
        );
    }

    // 구독 취소시 사용할 로직 -> 위에 메서드랑 변경할거야요
    @Transactional
    public void cancelSubscription(Long customerId, Long subscriptionId) {
        Subscription2 subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        if (!subscription.getCustomerId().equals(customerId)) {
            throw new IllegalStateException("해지 권한이 없습니다.");
        }

        try {
            // [외부 API 호출] 포트원 빌링키 삭제
            String billingKey = subscription.getPaymentMethod().getBillingKey();
            portOneApiClient.unsubscribeBillingKey(billingKey);

            // 성공 시 정상 해지 처리
            subscription.cancel();
            log.info("구독 해지 완료: SubID={}", subscriptionId);

        } catch (Exception e) {
            // [피드백 반영] 외부 API 실패 시 상태를 CANCEL_FAILED로 변경
            // 이렇게 해두면 나중에 관리자 페이지에서 '해지 실패건'만 모아서 재시도할 수 있습니다.
            subscription.updateStatus(SubscriptionStatus2.CANCEL_FAILED);

            log.error("포트원 빌링키 해지 중 오류 발생 - 관리자 확인 필요: SubID={}, Error={}",
                    subscriptionId, e.getMessage());

            // 사용자에게는 일단 알림을 주거나, 내부 정책에 따라 예외를 던질 수도 있습니다.
            throw new RuntimeException("결제 대행사 해지 통신 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }


     /**
      * [조회 서비스] 보안 체크 + DTO 변환까지 완료해서 반환
      */
        @Transactional(readOnly = true)
        public SubscriptionResponse2 getSubscriptionDto(Long customerId, Long subscriptionId) {
            Subscription2 sub = subscriptionRepository.findById(subscriptionId)
                    .orElseThrow(() -> new IllegalArgumentException("구독 내용 없음."));

            // [피드백 1] 보안 체크: 내 구독인지 확인
            validateOwner(customerId, sub.getCustomerId());

            // [피드백 3] Service에서 DTO로 변환하여 반환
            return SubscriptionResponse2.fromEntity(sub);
        }

        /**
         * [청구 내역 서비스] 보안 체크 + DTO 리스트 변환
         */
        @Transactional(readOnly = true)
        public List<BillingHistoryResponse> getBillingHistoryDto(Long customerId, Long subscriptionId) {
            Subscription2 sub = subscriptionRepository.findById(subscriptionId)
                    .orElseThrow(() -> new IllegalArgumentException("구독 내용 없음"));

            // 보안 체크
            validateOwner(customerId, sub.getCustomerId());

            List<SubscriptionBilling2> history = billingRepository.findAllBySubscriptionIdOrderByScheduledDateDesc(subscriptionId);

            return history.stream()
                    .map(BillingHistoryResponse::fromEntity)
                    .toList();
        }

        // 공통 보안 체크 로직
        private void validateOwner(Long requesterId, Long ownerId) {
            // 여기서 equals를 써서 두 ID가 같은지 비교합니다.
            if (!requesterId.equals(ownerId)) {
                throw new AccessDeniedException("해당 데이터에 접근할 권한이 없습니다.");
            }
        }




}


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





