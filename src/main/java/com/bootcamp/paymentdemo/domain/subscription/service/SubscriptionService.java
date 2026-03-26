package com.bootcamp.paymentdemo.domain.subscription.service;


import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.repository.CustomerRepository;
import com.bootcamp.paymentdemo.domain.payment.dto.Request.PaymentMethodCreateRequest;
import com.bootcamp.paymentdemo.domain.payment.entity.PaymentMethod;
import com.bootcamp.paymentdemo.domain.payment.enums.PgProvider;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentMethodRepository;
import com.bootcamp.paymentdemo.domain.payment.service.PortOneApiClient;
import com.bootcamp.paymentdemo.domain.subscription.dto.BillingContext;
import com.bootcamp.paymentdemo.domain.subscription.dto.response.CreateBillingResponse;
import com.bootcamp.paymentdemo.domain.subscription.dto.request.SubscriptionRequest;
import com.bootcamp.paymentdemo.domain.subscription.dto.request.SubscriptionUpdateRequest;
import com.bootcamp.paymentdemo.domain.subscription.dto.response.BillingHistoryResponse;
import com.bootcamp.paymentdemo.domain.subscription.dto.response.SubscriptionResponse;
import com.bootcamp.paymentdemo.domain.subscription.dto.response.SubscriptionStatusResponse;
import com.bootcamp.paymentdemo.domain.subscription.entity.*;
import com.bootcamp.paymentdemo.domain.subscription.repository.SubscriptionBillingRepository;
import com.bootcamp.paymentdemo.domain.subscription.repository.SubscriptionPlanRepository;
import com.bootcamp.paymentdemo.domain.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final SubscriptionBillingRepository billingRepository;
    private final PortOneApiClient portOneApiClient;
    private final CustomerRepository customerRepository;

    public Long initiateSubscription(Long customerId, Long planId, SubscriptionRequest request) {
        log.info("[구독프로세스 1단계] 시작 - customerId: {}, planId: {}", customerId, planId);

        // 1. DB 저장 단계 (트랜잭션 1)
        BillingContext context = savePendingSubscription(customerId, planId, request);
        log.info("[구독프로세스 2단계] DB 저장 완료 - subId: {}, billingId: {}", context.subscriptionId(), context.billingId());


        // 2. 포트원 API 호출 단계
        String paymentId = "SUB_" + context.billingId() + "_" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[구독프로세스 3단계] 포트원 결제 요청 시작 - paymentId: {}", paymentId);

        boolean isSuccess;
        try {
            isSuccess = portOneApiClient.payWithBillingKey(
                    context.billingKey(),
                    paymentId,
                    context.amount().intValue()
            );
        } catch (Exception e) {
            log.error("[구독프로세스 에러] 포트원 통신 중 예외 발생: {}", e.getMessage());
            // 통신 에러 시 '결제 요청 실패'로 업데이트 후 예외 던짐
            updatePaymentResult(context.subscriptionId(), context.billingId(), false, paymentId);
            throw new RuntimeException("결제 대행사 통신 실패", e); // 502 Bad Gateway 등으로 핸들링 권장
        }

        // 3. 결과 업데이트 단계 (트랜잭션 2)
        log.info("[구독프로세스 4단계] 결제 결과 업데이트 - 성공여부: {}", isSuccess);
        updatePaymentResult(context.subscriptionId(), context.billingId(), isSuccess, paymentId);

        if (!isSuccess) {
            log.warn("[구독프로세스 실패] 초기 결제 거절됨 - subId: {}", context.subscriptionId());
            // 비즈니스 예외로 던져서 핸들러에서 처리하게 함
            throw new IllegalStateException("초기 구독 결제 승인이 거절되었습니다.");
        }

        log.info("[구독프로세스 완료] 구독 및 첫 결제 성공 - subId: {}", context.subscriptionId());
        return context.subscriptionId();
    }

    @Transactional //이 메서드가 끝나면 DB 커넥션을 즉시 반납
    public BillingContext savePendingSubscription(Long customerId, Long planId, SubscriptionRequest request) {
        try {
            // [멱등성 체크 추가]
            // 오늘 날짜로 이미 'READY'나 'SUCCESS'인 청구가 있는지 확인합니다.
            // (첫 결제는 보통 '오늘'이 결제 예정일이 됩니다)
            LocalDateTime today = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);

            log.info("구독 생성 사전검증 시작 - customerId={}, planId={}, today={}",
                    customerId, planId, today);

            // 만약 이미 진행 중인 건이 있다면 새로 만들지 않고 기존 ID를 돌려주거나 예외를 던집니다.
            // 여기서는 안전하게 예외를 던져서 중복 진행을 막는 방식을 추천해요.
            boolean alreadyExists = billingRepository.existsByCustomerAndPlanAndDate(
                    customerId, planId, today);

            log.info("중복 결제 여부 확인 - customerId={}, planId={}, alreadyExists={}",
                    customerId, planId, alreadyExists);

            if (alreadyExists) {
                log.warn("이미 오늘 해당 플랜에 대한 결제 시도가 있었습니다. customerId={}", customerId);
                throw new IllegalStateException("이미 결제가 진행 중인 요청입니다.");
            }

            // 1. 플랜 조회
            SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플랜입니다."));

            log.info("플랜 조회 성공 - planId={}, planName={}, price={}",
                    plan.getId(), plan.getName(), plan.getPrice());

            Customer customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

            log.info("사용자 조회 성공 - customerId={}, email={}", customer.getId(), customer.getEmail());

            // 2. 결제 수단(카드 정보) 저장
            // 기존에 설정된 기본 카드가 있다면 해제 (정합성)
            paymentMethodRepository.findByCustomerIdAndIsDefaultTrue(customerId)
                    .ifPresent(paymentMethod -> {
                        log.info("기존 기본 결제수단 해제 - paymentMethodId={}", paymentMethod.getId());
                        paymentMethod.unsetDefault();
                    }); // 👈 이제 에러 없이 작동합니다!

            // create 메서드 인자인 PaymentMethodCreateRequest를 만들어줘야 합니다.
            PaymentMethodCreateRequest methodRequest = new PaymentMethodCreateRequest(
                    request.getBillingKey(),
                    request.getCustomerUid(),
                    PgProvider.TOSS_PAYMENTS, // Enum 타입 확인 필요
                    true
            );

            PaymentMethod method = PaymentMethod.create(customer, methodRequest);
            paymentMethodRepository.save(method);

            log.info("결제수단 저장 성공 - paymentMethodId={}, customerId={}, billingKey={}",
                    method.getId(), customerId, method.getBillingKey());
            // 3. 구독 정보 생성 (PENDING 상태)

            Subscription subscription = Subscription.builder()
                    .customer(customer)
                    .plan(plan)
                    .paymentMethod(method)
                    .status(SubscriptionStatus.ACTIVE)
                    .build();

            subscriptionRepository.save(subscription);

            log.info("구독 저장 성공 - subscriptionId={}, status={}",
                    subscription.getId(), subscription.getStatus());

            // 4. 구독 청구(Billing) 로그 생성 (READY 상태)
            // 4. Billing 저장
            SubscriptionBilling savedBilling = billingRepository.save(
                    SubscriptionBilling.builder()
                            .subscription(subscription)
                            .amount(plan.getPrice())
                            .scheduledDate(today)
                            .status(BillingStatus.READY)
                            .build());

            log.info("청구 로그 저장 성공 - billingId={}, subscriptionId={}, amount={}, status={}",
                    savedBilling.getId(), subscription.getId(), savedBilling.getAmount(), savedBilling.getStatus());


            // [개선] DB 조회 없이 방금 만든 객체들에서 바로 꺼내서 반환!
            return new BillingContext(
                    savedBilling.getId(),      // 여기서 사용!
                    subscription.getId(),
                    method.getBillingKey(),
                    plan.getPrice(),
                    customerId
            );
        } catch (DataIntegrityViolationException e) {
            // [2차 방어] DB 유니크 인덱스에 걸렸을 때 (레이스 컨디션 발생 시)
            log.error("구독 생성 DB 오류 - customerId={}, planId={}, message={}",
                    customerId, planId, e.getMessage(), e);
            throw new IllegalStateException("이미 결제가 진행 중인 요청입니다. 잠시 후 다시 시도해주세요.");
        }
    }

    @Transactional // 3번 단계를 위한 짧은 트랜잭션
    public void updatePaymentResult(Long subId, Long billingId, boolean isSuccess, String paymentId) {
        Subscription sub = subscriptionRepository.findById(subId)
                .orElseThrow(() -> new IllegalArgumentException("구독 정보를 찾을 수 없습니다."));
        SubscriptionBilling billing = billingRepository.findById(billingId)
                .orElseThrow(() -> new IllegalArgumentException("결제 로그를 찾을 수 없습니다."));

        if (isSuccess) {
            billing.markRequested(paymentId);
//            activateSubscription(subId);
            sub.activate();

            // 상태를 REQUESTED로 변경 (웹훅 대기)
        } else {
            billing.setPaymentId(paymentId);
            billing.fail("정기 결제 요청에 실패했습니다.");
            sub.toPastDue();         // 미납 처리

            log.warn("결제 요청 실패: billingId={}, paymentId={}", billingId, paymentId);
        }
    }


    @Transactional
    public void confirmSubscription(String paymentId) {
        // 1. paymentid 수정
        SubscriptionBilling billing = billingRepository.findByPaymentId(paymentId)
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


    @Transactional
    public void executeRecurringBilling(Long subId, String paymentId) { // [피드백 3] 엔티티 대신 ID 전달
        // 1. 최신 상태 조회를 통해 영속성 컨텍스트 및 Dirty Checking 보장
        Subscription sub = subscriptionRepository.findById(subId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        // [피드백 1] 실행 시점 중복 체크 (조회 시점과 실행 시점 사이의 간극 방어)
        // UNIQUE 제약 조건(subscription_id, scheduled_date)과 함께 사용하면 완벽합니다.
        LocalDateTime targetDate = sub.getNextBillingDate();
        if (billingRepository.existsBySubscriptionAndScheduledDate(sub, targetDate)) {
            log.warn("이미 처리 중이거나 완료된 결제 건입니다. Skip 처리: SubID={}, Date={}", subId, targetDate);
            return;
        }

        if (sub.getStatus() != SubscriptionStatus.ACTIVE) return;

        // 2. 장부 생성 (기존 로직)
        BillingContext context = prepareRecurringBilling(sub);
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
                sub.getCustomer().getId()
        );
    }

    @Transactional
    public SubscriptionStatusResponse updateSubscription(Long customerId, Long subscriptionId, SubscriptionUpdateRequest request) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        if (!subscription.getCustomer().getId().equals(customerId)) {
            throw new IllegalStateException("구독 상태 변경 권한이 없습니다.");
        }

        if (!"cancel".equalsIgnoreCase(request.getAction())) {
            throw new IllegalArgumentException("지원하지 않는 액션입니다. action=" + request.getAction());
        }

        try {
            String billingKey = subscription.getPaymentMethod().getBillingKey();
            portOneApiClient.unsubscribeBillingKey(billingKey);

            subscription.cancel();

            String message = "구독이 해지되었습니다.";
            if (request.getReason() != null && !request.getReason().isBlank()) {
                message += " reason=" + request.getReason();
            }

            log.info("구독 해지 완료: subId={}, reason={}", subscriptionId, request.getReason());
            return new SubscriptionStatusResponse(subscriptionId, SubscriptionStatus.CANCELED, message);

        } catch (Exception e) {
            subscription.updateStatus(SubscriptionStatus.CANCEL_FAILED);

            log.error("구독 해지 중 오류 발생: subId={}, error={}", subscriptionId, e.getMessage());
            throw new RuntimeException("구독 해지 처리 중 오류가 발생했습니다.");
        }
    }

    // 구독 취소시 사용할 로직 -> 위에 메서드랑 변경할거야요
    @Transactional
    public void cancelSubscription(Long customerId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        if (!subscription.getCustomer().getId().equals(customerId)) {
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
            subscription.updateStatus(SubscriptionStatus.CANCEL_FAILED);

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
    public SubscriptionResponse getSubscriptionDto(Long customerId, Long subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("구독 내용 없음."));

        // [피드백 1] 보안 체크: 내 구독인지 확인
        validateOwner(customerId, sub.getCustomer().getId());

        // [피드백 3] Service에서 DTO로 변환하여 반환
        return SubscriptionResponse.fromEntity(sub);
    }

    /**
     * [청구 내역 서비스] 보안 체크 + DTO 리스트 변환
     */
    @Transactional(readOnly = true)
    public List<BillingHistoryResponse> getBillingHistoryDto(Long customerId, Long subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("구독 내용 없음"));

        // 보안 체크
        validateOwner(customerId, sub.getCustomer().getId());

        List<SubscriptionBilling> history = billingRepository.findAllBySubscriptionIdOrderByScheduledDateDesc(subscriptionId);

        List<BillingHistoryResponse> responseList = new ArrayList<>();

        // 3. for-each 문을 돌면서 엔티티를 DTO로 변환해 리스트에 추가합니다.
        for (SubscriptionBilling billing : history) {
            BillingHistoryResponse dto = BillingHistoryResponse.fromEntity(billing);
            responseList.add(dto);
        }

        // 4. 결과 리스트를 반환합니다.
        return responseList;
    }

    // 공통 보안 체크 로직
    private void validateOwner(Long requesterId, Long ownerId) {
        // 여기서 equals를 써서 두 ID가 같은지 비교합니다.
        if (!requesterId.equals(ownerId)) {
            throw new AccessDeniedException("해당 데이터에 접근할 권한이 없습니다.");
        }
    }


    /**
     * 활성화된 모든 구독 플랜 목록을 조회합니다.
     *
     * @return SubscriptionPlan 엔티티 리스트
     */
    @Transactional(readOnly = true) // 조회 전용이므로 성능 최적화!
    public List<SubscriptionPlan> getActivePlans() {
        log.info("DB에서 모든 구독 플랜을 조회합니다.");

        return subscriptionPlanRepository.findAllByStatusOrderByPriceAsc(PlanStatus.ACTIVE);
    }

    @Transactional
    public CreateBillingResponse createBilling(Long customerId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        validateOwner(customerId, subscription.getCustomer().getId());

        String paymentId = "SUB_MANUAL_" + subscriptionId + "_" + UUID.randomUUID().toString().substring(0, 8);
        executeRecurringBilling(subscriptionId, paymentId);

        SubscriptionBilling billing = billingRepository.findTopBySubscriptionIdOrderByIdDesc(subscriptionId)
                .orElseThrow(() -> new IllegalStateException("청구 결과를 찾을 수 없습니다."));

        if (billing.getStatus() == BillingStatus.FAILED) {
            throw new IllegalStateException(
                    billing.getErrorMessage() != null ? billing.getErrorMessage() : "청구 실행에 실패했습니다."
            );
        }

        return CreateBillingResponse.fromEntity(billing);
    }

    //-------------------------------------------------------------------------------------------
    // 유예 기간
    private static final int PAST_DUE_DAYS = 7;

    private final SubscriptionBillingRepository subscriptionBillingRepository;

    // 구독 생성 -> 년간, 월간 -> 결제 성공 -> ACTIVE(구독 활성)
    @Transactional
    public void activateSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("구독이 없습니다. id는" + subscriptionId));

        if (subscription.getStatus() == SubscriptionStatus.ENDED) {
            throw new IllegalStateException("종료된 구독은 활성화할 수 없습니다.");
        }

        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            throw new IllegalStateException("해지된 구독은 다시 활성화할 수 없습니다.");
        }
        subscription.activate();
    }


    // 구독 생성 -> 결제 성공 -> ACTIVE(구독활성)
    @Transactional
    public void activateSubscriptionAfterCreateSuccess(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        if (subscription.getStatus() != SubscriptionStatus.PENDING
                && subscription.getStatus() != SubscriptionStatus.PAST_DUE) {
            throw new IllegalStateException("활성화할 수 없는 구독 상태입니다. 현재상태는" + subscription.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        subscription.updateStatus(SubscriptionStatus.ACTIVE);
        subscription.setNextBillingDate(subscription.getPlan().calculateNextBillingDate(now));
    }

    // 구독 생성 -> 결제 실패 -> PAST_DUE(결제 연체)
    @Transactional
    public void markPastDueAfterCreateFailure(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        if (subscription.getStatus() != SubscriptionStatus.PENDING
                && subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("연체 처리할 수 없는 구독 상태입니다. 현재상태는" + subscription.getStatus());
        }

        subscription.toPastDue();
    }

    // PAST_DUE(결제 연체) -> 구독활성 -> 일수 기준에따라 정지 상태 변화
    @Transactional
    public void updatePastDueStatusByRemainingDays(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        if (subscription.getStatus() != SubscriptionStatus.PAST_DUE) {
            throw new IllegalStateException("연체 상태 구독만 확인할 수 있습니다. 현재상태는" + subscription.getStatus());
        }

        if (isPastDueGraceExpired(subscription)) {
            subscription.updateStatus(SubscriptionStatus.ENDED);
            subscription.setNextBillingDate(null);
            return;
        }

        subscription.updateStatus(SubscriptionStatus.PAST_DUE);
    }

    // PAST_DUE(결제 연체) -> 기한내 잔금 지불 함 -> 구독 활성
    @Transactional
    public void recoverPastDueSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        if (subscription.getStatus() != SubscriptionStatus.PAST_DUE) {
            throw new IllegalStateException("연체 상태 구독만 복구할 수 있습니다. 현재상태는" + subscription.getStatus());
        }

        if (isPastDueGraceExpired(subscription)) {
            throw new IllegalStateException("연체 기한이 지나 복구할 수 없습니다.");
        }

        LocalDateTime baseDate = subscription.getNextBillingDate() == null
                ? LocalDateTime.now()
                : subscription.getNextBillingDate();

        subscription.updateStatus(SubscriptionStatus.ACTIVE);
        subscription.setNextBillingDate(subscription.getPlan().calculateNextBillingDate(baseDate));
    }

    // PAST_DUE(결제 연체) -> 기한내 잔금 지불 안함 -> 정지됨
    @Transactional
    public void endPastDueSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        if (subscription.getStatus() != SubscriptionStatus.PAST_DUE) {
            throw new IllegalStateException("연체 상태 구독만 종료할 수 있습니다. 현재상태는" + subscription.getStatus());
        }

        if (!isPastDueGraceExpired(subscription)) {
            throw new IllegalStateException("아직 연체 기한이 남아 있습니다.");
        }

        subscription.updateStatus(SubscriptionStatus.ENDED);
        subscription.setNextBillingDate(null);
    }

    // 구독 활성 -> 구독 갱신 -> 년간, 월간 기간 연장
    @Transactional
    public void renewSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("활성 구독만 갱신할 수 있습니다. 현재상태는" + subscription.getStatus());
        }

        LocalDateTime baseDate = subscription.getNextBillingDate() == null
                ? LocalDateTime.now()
                : subscription.getNextBillingDate();

        subscription.setNextBillingDate(subscription.getPlan().calculateNextBillingDate(baseDate));
    }

    // 구독 활성 -> 취소 요청 -> 남은 기간 사용 후 종료 -> CANCELED(해지됨)
    @Transactional
    public void requestCancelSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("활성 구독만 해지 요청할 수 있습니다. 현재상태는" + subscription.getStatus());
        }

        subscription.updateStatus(SubscriptionStatus.CANCELED);
    }

    // 구독 활성 -> 구독 기간 종료 -> 잔여일 기준 초과 안함 -> 잔금 안함 -> ENDED(이용 종료)
    @Transactional
    public void endSubscriptionWithoutRemainingDays(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        long remainingDays = calculateRemainingDays(subscription);
        long settlementAmount = calculateSettlementAmount(subscription);

        if (remainingDays > 0) {
            throw new IllegalStateException("아직 남은 기간이 있습니다. 잔여일은" + remainingDays);
        }

        if (settlementAmount > 0) {
            throw new IllegalStateException("잔금이 남아 있어 종료할 수 없습니다. 잔금은" + settlementAmount);
        }

        subscription.updateStatus(SubscriptionStatus.ENDED);
        subscription.setNextBillingDate(null);
    }

    // 구독 활성 -> 구독 기간 종료 -> 잔여일 기준 초과 함 -> 잔금 지불함 -> CANCELED(해지됨)
    @Transactional
    public void cancelSubscriptionAfterSettlementPaid(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        long remainingDays = calculateRemainingDays(subscription);
        long settlementAmount = calculateSettlementAmount(subscription);

        if (remainingDays <= 0) {
            throw new IllegalStateException("남은 기간이 없는 구독입니다.");
        }

        if (settlementAmount <= 0) {
            throw new IllegalStateException("지불할 잔금이 없습니다.");
        }

        billingRepository.save(SubscriptionBilling.builder()
                .subscription(subscription)
                .amount(settlementAmount)
                .scheduledDate(LocalDateTime.now())
                .status(BillingStatus.SUCCESS)
                .build());

        subscription.updateStatus(SubscriptionStatus.CANCELED);
    }

    // 구독 활성 -> 구독 기간 종료 -> 잔여일 기준 초과 함 -> 잔금 지불 안함 -> 정지됨
    @Transactional
    public void markPastDueAfterSettlementFailure(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        long remainingDays = calculateRemainingDays(subscription);
        long settlementAmount = calculateSettlementAmount(subscription);

        if (remainingDays <= 0) {
            throw new IllegalStateException("남은 기간이 없는 구독입니다.");
        }

        if (settlementAmount <= 0) {
            throw new IllegalStateException("지불할 잔금이 없습니다.");
        }

        billingRepository.save(SubscriptionBilling.builder()
                .subscription(subscription)
                .amount(settlementAmount)
                .scheduledDate(LocalDateTime.now())
                .status(BillingStatus.FAILED)
                .errorMessage("잔금 미납")
                .build());

        subscription.toPastDue();
    }

    private boolean isPastDueGraceExpired(Subscription subscription) {
        if (subscription.getNextBillingDate() == null) {
            return true;
        }

        return subscription.getNextBillingDate()
                .plusDays(PAST_DUE_DAYS)
                .isBefore(LocalDateTime.now());
    }

    private long calculateRemainingDays(Subscription subscription) {
        if (subscription.getNextBillingDate() == null) {
            return 0L;
        }

        if (!subscription.getNextBillingDate().isAfter(LocalDateTime.now())) {
            return 0L;
        }

        return ChronoUnit.DAYS.between(LocalDateTime.now(), subscription.getNextBillingDate());
    }

    private long calculateSettlementAmount(Subscription subscription) {
        long remainingDays = calculateRemainingDays(subscription);

        if (remainingDays <= 0) {
            return 0L;
        }

        return subscription.getPlan().getPrice();
    }
}
