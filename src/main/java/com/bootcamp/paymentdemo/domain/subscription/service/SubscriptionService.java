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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    private final RedissonClient redissonClient; // 동시성 제어를 위한 Redisson 추가

    // 순환 참조(Circular Reference) 해결을 위해 필드 주입 + @Lazy 적용
    private SubscriptionService self;

    @Autowired
    public void setSelf(@Lazy SubscriptionService self) {
        this.self = self;
    }

    public Long initiateSubscription(Long customerId, Long planId, SubscriptionRequest request) {
        log.info("[구독프로세스 1단계] 시작 - customerId: {}, planId: {}", customerId, planId);

        // [동시성 방어] 사용자별 락 획득 (따닥 방지)
        String lockKey = "lock:subscription:init:" + customerId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(3, 5, TimeUnit.SECONDS);
            if (!isLocked) {
                log.warn("[동시성 락] 이미 진행 중인 결제 요청입니다. customerId: {}", customerId);
                throw new IllegalStateException("이미 결제가 진행 중인 요청입니다. 잠시 후 다시 시도해주세요.");
            }

            // 1. DB 저장 단계 (트랜잭션 1) - self 프록시 호출
            BillingContext context = self.savePendingSubscription(customerId, planId, request);
            log.info("[구독프로세스 2단계] DB 저장 완료 - subId: {}, billingId: {}", context.subscriptionId(), context.billingId());

            // 2. 포트원 API 호출 단계
            String paymentId = "SUB_INIT" + context.billingId();
            // [멱등성 방어] 고유한 멱등키 생성
            String idempotencyKey = "INIT_" + customerId + "_" + context.billingId() + "_" + UUID.randomUUID().toString().substring(0, 8);

            log.info("[구독프로세스 3단계] 포트원 결제 요청 시작 - paymentId: {}, idempotencyKey: {}", paymentId, idempotencyKey);

            boolean isSuccess;
            try {
                isSuccess = portOneApiClient.payWithBillingKey(
                        context.billingKey(),
                        paymentId,
                        context.amount().intValue(),
                        idempotencyKey // 멱등키 전달
                );
            } catch (Exception e) {
                log.error("[구독프로세스 에러] 포트원 통신 중 예외 발생: {}", e.getMessage());
                self.updatePaymentResult(context.subscriptionId(), context.billingId(), false, paymentId);
                throw new RuntimeException("결제 대행사 통신 실패", e);
            }

            // 3. 결과 업데이트 단계 (트랜잭션 2)
            log.info("[구독프로세스 4단계] 결제 결과 업데이트 - 성공여부: {}", isSuccess);
            self.updatePaymentResult(context.subscriptionId(), context.billingId(), isSuccess, paymentId);

            if (!isSuccess) {
                log.warn("[구독프로세스 실패] 초기 결제 거절됨 - subId: {}", context.subscriptionId());
                throw new IllegalStateException("초기 구독 결제 승인이 거절되었습니다.");
            }

            log.info("[구독프로세스 완료] 구독 및 첫 결제 성공 - subId: {}", context.subscriptionId());
            return context.subscriptionId();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("결제 처리 중 서버 지연이 발생했습니다.", e);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Transactional
    public BillingContext savePendingSubscription(Long customerId, Long planId, SubscriptionRequest request) {
        try {
            LocalDateTime today = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);

            log.info("구독 생성 사전검증 시작 - customerId={}, planId={}, today={}",
                    customerId, planId, today);

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
            paymentMethodRepository.findByCustomerIdAndIsDefaultTrue(customerId)
                    .ifPresent(paymentMethod -> {
                        log.info("기존 기본 결제수단 해제 - paymentMethodId={}", paymentMethod.getId());
                        paymentMethod.unsetDefault();
                    });

            PaymentMethodCreateRequest methodRequest = new PaymentMethodCreateRequest(
                    request.getBillingKey(),
                    request.getCustomerUid(),
                    PgProvider.TOSS_PAYMENTS,
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
                    .status(SubscriptionStatus.PENDING)
                    .build();

            subscriptionRepository.save(subscription);

            log.info("구독 저장 성공 - subscriptionId={}, status={}",
                    subscription.getId(), subscription.getStatus());

            // 4. 구독 청구(Billing) 로그 생성 (READY 상태)
            SubscriptionBilling savedBilling = billingRepository.save(
                    SubscriptionBilling.builder()
                            .subscription(subscription)
                            .amount(plan.getPrice())
                            .scheduledDate(today)
                            .status(BillingStatus.READY)
                            .build());

            log.info("청구 로그 저장 성공 - billingId={}, subscriptionId={}, amount={}, status={}",
                    savedBilling.getId(), subscription.getId(), savedBilling.getAmount(), savedBilling.getStatus());

            return new BillingContext(
                    savedBilling.getId(),
                    subscription.getId(),
                    method.getBillingKey(),
                    plan.getPrice(),
                    customerId
            );
        } catch (DataIntegrityViolationException e) {
            log.error("구독 생성 DB 오류 - customerId={}, planId={}, message={}",
                    customerId, planId, e.getMessage(), e);
            throw new IllegalStateException("이미 결제가 진행 중인 요청입니다. 잠시 후 다시 시도해주세요.");
        }
    }

    @Transactional
    public void updatePaymentResult(Long subId, Long billingId, boolean isSuccess, String paymentId) {
        Subscription sub = subscriptionRepository.findById(subId)
                .orElseThrow(() -> new IllegalArgumentException("구독 정보를 찾을 수 없습니다."));
        SubscriptionBilling billing = billingRepository.findById(billingId)
                .orElseThrow(() -> new IllegalArgumentException("결제 로그를 찾을 수 없습니다."));

        if (isSuccess) {
            billing.markRequested(paymentId);
            log.info("결제 성공! 이제 ACTIVE로 바꿉니다. 현재상태: {}", sub.getStatus());
            sub.activate();
            log.info("변경 후 상태: {}", sub.getStatus());
        } else {
            billing.setPaymentId(paymentId);
            billing.fail("정기 결제 요청에 실패했습니다.");
            sub.toPastDue();

            log.warn("결제 요청 실패: billingId={}, paymentId={}", billingId, paymentId);
        }
    }


    @Transactional
    public void confirmSubscription(String paymentId) {
        SubscriptionBilling billing = billingRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 결제 건입니다."));

        if (!billing.getPaymentId().equals(paymentId)) {
            log.error("결제 ID 불일치 보안 위협 감지! DB: {}, Webhook: {}",
                    billing.getPaymentId(), paymentId);
            throw new IllegalStateException("잘못된 결제 식별자 요청입니다.");
        }

        if (billing.isCompleted()) {
            log.info("이미 처리가 완료된 결제 건입니다. paymentId={}", paymentId);
            return;
        }

        billing.complete(paymentId);
        Subscription subscription = billing.getSubscription();
        subscription.activate();

        log.info("구독 최종 활성화 완료: SubID={}, PaymentID={}",
                subscription.getId(), paymentId);
    }

    // extractBillingId method has been removed as it was dead code.


    @Transactional
    public void executeRecurringBilling(Long subId, String paymentId) {
        Subscription sub = subscriptionRepository.findById(subId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구독입니다."));

        LocalDateTime targetDate = sub.getNextBillingDate();
        if (billingRepository.existsBySubscriptionAndScheduledDate(sub, targetDate)) {
            log.warn("이미 처리 중이거나 완료된 결제 건입니다. Skip 처리: SubID={}, Date={}", subId, targetDate);
            return;
        }

        if (sub.getStatus() != SubscriptionStatus.ACTIVE) return;

        BillingContext context = prepareRecurringBilling(sub);
        if (context == null) return;

        log.info("정기 결제 API 호출 시작: SubID={}, PaymentID={}", subId, paymentId);

        // 정기 결제용 멱등키 생성
        String idempotencyKey = "RECUR_" + subId + "_" + context.billingId();

        boolean isSuccess = portOneApiClient.payWithBillingKey(
                context.billingKey(),
                paymentId,
                context.amount().intValue(),
                idempotencyKey
        );

        self.updatePaymentResult(sub.getId(), context.billingId(), isSuccess, paymentId);
    }


    @Transactional
    public BillingContext prepareRecurringBilling(Subscription sub) {
        LocalDateTime targetDate = sub.getNextBillingDate();
        if (billingRepository.existsBySubscriptionAndScheduledDate(sub, targetDate)) {
            log.warn("이미 처리 중인 결제 건입니다. SubID: {}, Date: {}", sub.getId(), targetDate);
            return null;
        }

        SubscriptionBilling billing = billingRepository.save(SubscriptionBilling.builder()
                .subscription(sub)
                .amount(sub.getPlan().getPrice())
                .scheduledDate(targetDate)
                .status(BillingStatus.READY)
                .build());

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

        if (request.getAction() != com.bootcamp.paymentdemo.domain.subscription.enums.SubscriptionAction.CANCEL) {
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




    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscriptionDto(Long customerId, Long subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("구독 내용 없음."));

        validateOwner(customerId, sub.getCustomer().getId());
        return SubscriptionResponse.fromEntity(sub);
    }

    @Transactional(readOnly = true)
    public List<BillingHistoryResponse> getBillingHistoryDto(Long customerId, Long subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("구독 내용 없음"));

        validateOwner(customerId, sub.getCustomer().getId());

        List<SubscriptionBilling> history = billingRepository.findAllBySubscriptionIdOrderByScheduledDateDesc(subscriptionId);
        List<BillingHistoryResponse> responseList = new ArrayList<>();

        for (SubscriptionBilling billing : history) {
            BillingHistoryResponse dto = BillingHistoryResponse.fromEntity(billing);
            responseList.add(dto);
        }

        return responseList;
    }

    private void validateOwner(Long requesterId, Long ownerId) {
        if (!requesterId.equals(ownerId)) {
            throw new AccessDeniedException("해당 데이터에 접근할 권한이 없습니다.");
        }
    }

    @Transactional(readOnly = true)
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
        self.executeRecurringBilling(subscriptionId, paymentId);

        SubscriptionBilling billing = billingRepository.findTopBySubscriptionIdOrderByIdDesc(subscriptionId)
                .orElseThrow(() -> new IllegalStateException("청구 결과를 찾을 수 없습니다."));

        if (billing.getStatus() == BillingStatus.FAILED) {
            throw new IllegalStateException(
                    billing.getErrorMessage() != null ? billing.getErrorMessage() : "청구 실행에 실패했습니다."
            );
        }

        return CreateBillingResponse.fromEntity(billing);
    }

 }