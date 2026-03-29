package com.bootcamp.paymentdemo.domain.payment.service;

import com.bootcamp.paymentdemo.config.DistributedLock;
import com.bootcamp.paymentdemo.domain.order.entity.Order;
import com.bootcamp.paymentdemo.domain.order.entity.OrderStatus;
import com.bootcamp.paymentdemo.domain.order.repository.OrderRepository;
import com.bootcamp.paymentdemo.domain.payment.dto.Request.PaymentCreateReadyRequest;
import com.bootcamp.paymentdemo.domain.payment.dto.Request.PortOneWebhookRequest;
import com.bootcamp.paymentdemo.domain.payment.dto.Response.*;
import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentStatus;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentRepository;
import com.bootcamp.paymentdemo.global.error.PortOneApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PortOneApiClient portOneApiClient;
    private final PaymentRetryTaskService paymentRetryTaskService;
    private final PaymentLifecycleService paymentLifecycleService;
    private final PaymentAccessValidator paymentAccessValidator;



    /**
     * 결제 시도(Attempt) 생성
     * 프론트에서 /checkout-ready를 호출하면 여기로 들어옵니다.
     * 이 단계에서는 "결제 확정"이 아니라, 결제를 시작하기 위한 준비 레코드만 생성합니다.
     */
    @Transactional
    public PaymentCreateReadyResponse create(Authentication authentication, PaymentCreateReadyRequest request) {
        paymentAccessValidator.validateAuthenticated(authentication);

        if (request.totalAmount() == null || request.totalAmount() <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }

        Order order = orderRepository.findByOrderId(request.orderId()).orElseThrow(
                () -> new IllegalArgumentException("없는 주문번호")
        );

        long point = request.pointsToUse() == null ? 0L : request.pointsToUse();

        Long expectedAmount = order.getTotalAmount().longValue();
        if (!expectedAmount.equals(request.totalAmount())) {
            throw new IllegalArgumentException("주문 금액과 결제 금액은 같아야 합니다.");
        }
        if (point < 0) {
            throw new IllegalArgumentException("사용 포인트는 0 이상이어야 합니다.");
        }
        if (point > expectedAmount) {
            throw new IllegalArgumentException("사용 포인트가 주문 금액보다 클 수 없습니다.");
        }

        paymentAccessValidator.validateOrderOwnership(authentication, order);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("결제를 시작할 수 없는 주문 상태입니다. status=" + order.getStatus());
        }

        Payment existingReadyPayment = paymentRepository
                .findTopByOrderAndStatusInOrderByCreatedAtDesc(order, List.of(PaymentStatus.READY))
                .orElse(null);

        if (existingReadyPayment != null) {
            if (existingReadyPayment.getExpiresAt() != null
                    && existingReadyPayment.getExpiresAt().isBefore(LocalDateTime.now())) {
                existingReadyPayment.expire();
            } else if (existingReadyPayment.getUsePoint().equals(point)) {
                log.info("기존 READY 결제를 재사용합니다. orderId={}, paymentId={}",
                        order.getOrderId(), existingReadyPayment.getPaymentId());
                return PaymentCreateReadyResponse.checkoutReady(existingReadyPayment);
            } else {
                throw new IllegalStateException(
                        "이미 진행 중인 결제 요청이 있습니다. 잠시 후 다시 시도해주세요. paymentId="
                                + existingReadyPayment.getPaymentId()
                );
            }
        }

        String paymentId = generatePaymentId();
        Payment payment = Payment.of(order, expectedAmount, paymentId,point);
        paymentRepository.save(payment);

        return PaymentCreateReadyResponse.checkoutReady(payment);
    }

    /**
     * 결제 확정
     * 흐름:
     * 1) paymentId 소유권 검증 및 결제건 조회
     * 2) 이미 처리된 건이면 멱등 응답으로 즉시 반환
     * 3) pgAmount == 0 이면 PortOne 없이 내부 완료 처리
     * 4) 그 외에는 PortOne 결제 단건조회로 실제 상태/금액 검증
     * 5) 성공이면 완료 처리, 실패/미확정이면 상태에 따라 실패 또는 재시도 등록
     */
    @DistributedLock(key = "'lock:payment:confirm:' + #paymentId", waitTime = 3, leaseTime = 10)
    public PaymentConfirmResponse confirm(Authentication authentication, String paymentId) {
        Payment payment = paymentAccessValidator.getAuthorizedPayment(authentication, paymentId);

        // 이미 성공한건 멱등처리
        if (payment.isAlreadyProcessed()) {
            log.info("멱등 처리 - 이미 처리된 결제입니다. paymentId={}, status={}",
                    paymentId, payment.getStatus());
            return PaymentConfirmResponse.alreadyProcessed(payment);
        }

        // 주문 금액 전부를 포인트로 결제한 경우
        if (payment.getPgAmount() == 0L) {
            try {
                paymentLifecycleService.completePointOnlyPayment(paymentId);
            } catch (Exception processingException) {
                return PaymentConfirmResponse.failed(
                        paymentLifecycleService.getPayment(paymentId),
                        "내부 처리 실패: " + processingException.getMessage()
                );
            }
            return PaymentConfirmResponse.success(paymentLifecycleService.getPayment(paymentId));
        }


        PortOnePaymentInfoResponse portOnePayment;
        String verifyIdempotencyKey = portOneApiClient.buildVerifyIdempotencyKey(paymentId);
        try {
            portOnePayment = portOneApiClient.getPaymentInfo(paymentId, verifyIdempotencyKey);
        } catch (PortOneApiException apiException) {
            if (apiException.isRetryable()) {
                paymentRetryTaskService.enqueueVerifyRetry(paymentId, verifyIdempotencyKey);
                return PaymentConfirmResponse.failed(
                        payment,
                        "포트원 조회 일시 장애로 결제 확인 재시도를 등록했습니다. reason=" + apiException.getMessage()
                );
            }
            paymentLifecycleService.markFailed(paymentId);
            return PaymentConfirmResponse.failed(
                    paymentLifecycleService.getPayment(paymentId),
                    "포트원 조회 실패(비재시도): " + apiException.getMessage()
                );
        }
        if (portOnePayment.isPaidStatus()) {
            try {
                paymentLifecycleService.completeApprovedPayment(paymentId, portOnePayment);
            } catch (Exception processingException) {
                String compensationMessage = paymentLifecycleService.compensateApprovedPayment(
                        paymentId,
                        "결제 확정 후 내부 처리 실패로 취소"
                );
                log.error("결제 확정 후 내부 처리 실패 - paymentId={}, message={}",
                        paymentId, processingException.getMessage(), processingException);

                return PaymentConfirmResponse.failed(
                        paymentLifecycleService.getPayment(paymentId),
                        "내부 처리 실패: " + processingException.getMessage() + " | " + compensationMessage
                );
            }

            return PaymentConfirmResponse.success(paymentLifecycleService.getPayment(paymentId));
        } else if (portOnePayment.isTerminalFailureStatus()) {
            paymentLifecycleService.markFailed(paymentId);
            return PaymentConfirmResponse.failed(
                    paymentLifecycleService.getPayment(paymentId),
                    "포트원 결제 실패. status=" + portOnePayment.getStatus()
                            + ", reason=" + portOnePayment.resolveFailureReason()
            );
        } else {
            // READY/PENDING 같은 미확정 상태는 즉시 실패시키지 않고 verify 재시도 큐에 넣는다.
            paymentRetryTaskService.enqueueVerifyRetry(paymentId, verifyIdempotencyKey);
            return PaymentConfirmResponse.failed(
                    payment,
                    "포트원 결제 상태 확인 중입니다. status=" + portOnePayment.getStatus()
            );
        }
    }
    /**
     * 포트원 웹훅 처리
     * Webhook은 Client Confirm과 동일한 "결제 확정 검증 규칙"을 따라야 합니다.
     * (상태/금액/상점 검증, 멱등성 보장)
     *
     * 컨트롤러에서 IllegalArgumentException을 분기 처리하고 있으므로,
     * 비즈니스적으로 거절해야 하는 상황은 IllegalArgumentException으로 올립니다.
     */
    public void processWebhook(String webhookId, PortOneWebhookRequest request) {
        if (request == null || request.getPaymentId() == null) {
            throw new IllegalArgumentException("웹훅 요청 형식이 올바르지 않습니다. paymentId가 없습니다.");
        }

        String paymentId = request.getPaymentId();
        String verifyIdempotencyKey = portOneApiClient.buildVerifyIdempotencyKey(paymentId);
        log.info("웹훅 처리 시작 - webhookId={}, paymentId={}, status={}",
                webhookId, paymentId, request.getStatus());

        Payment payment = paymentLifecycleService.getPayment(paymentId);

        if (payment.isAlreadyProcessed()) {
            log.info("웹훅 멱등 처리 - 이미 처리된 결제입니다. paymentId={}, status={}",
                    paymentId, payment.getStatus());
            return;
        }

        PortOnePaymentInfoResponse portOnePayment;
        try {
            portOnePayment = portOneApiClient.getPaymentInfo(paymentId, verifyIdempotencyKey);
        } catch (PortOneApiException apiException) {
            if (apiException.isRetryable()) {
                paymentRetryTaskService.enqueueVerifyRetry(paymentId, verifyIdempotencyKey);
                throw new IllegalArgumentException(
                        "웹훅 처리 중 포트원 조회 일시 장애(재시도 등록): " + apiException.getMessage()
                );
            }
            paymentLifecycleService.markFailed(paymentId);
            throw new IllegalArgumentException("웹훅 처리 중 포트원 조회 실패(비재시도): " + apiException.getMessage());
        }

        if (!portOnePayment.isPaidStatus()) {
            paymentLifecycleService.markFailed(paymentId);
            throw new IllegalArgumentException("웹훅 검증 실패: 결제 상태가 PAID가 아닙니다. status="
                    + portOnePayment.getStatus() + ", reason=" + portOnePayment.resolveFailureReason());
        }

        try {
            paymentLifecycleService.completeApprovedPayment(paymentId, portOnePayment);
        } catch (Exception processingException) {
            String compensationMessage = paymentLifecycleService.compensateApprovedPayment(
                    paymentId,
                    "웹훅 처리 중 내부 실패로 취소"
            );
            throw new IllegalArgumentException(
                    "웹훅 처리 실패: " + processingException.getMessage() + " | " + compensationMessage
            );
        }

        log.info("웹훅 처리 완료 - paymentId={}, finalStatus={}", paymentId, paymentLifecycleService.getPayment(paymentId).getStatus());
    }

    /**
     * 결제 ID 생성기
     */
    public static String generatePaymentId() {
        String random = UUID.randomUUID().toString().replace("-", "");
        return "pay-" + random;
    }

    // 주문 조회 화면에서 결제 기준 요약 정보를 붙일 때 사용하는 메서드
    public PaymentSummaryResponse getPaymentSummary(Authentication authentication, String paymentId) {
        Payment payment = paymentAccessValidator.getAuthorizedPayment(authentication, paymentId);
        return PaymentSummaryResponse.from(payment);
    }

    /**
     * 결제 상세 조회 (PortOne 기준)
     * - paymentId를 받아 PortOne 단건조회 결과를 우리 DTO로 변환해 반환합니다.
     * - 주문/환불/관리 화면에서 PortOne 단건조회 결과가 필요할 때 재사용합니다.
     * - 현재 프론트에서는 아직 직접 사용하지 않습니다.
     */
    @Transactional(readOnly = true)
    public PaymentDetailResponse getPaymentDetail(Authentication authentication, String paymentId) {
        paymentAccessValidator.getAuthorizedPayment(authentication, paymentId);

        String idempotencyKey = portOneApiClient.buildVerifyIdempotencyKey(paymentId);
        PortOnePaymentInfoResponse info = portOneApiClient.getPaymentInfo(paymentId, idempotencyKey);
        return PaymentDetailResponse.from(info);
    }

}
