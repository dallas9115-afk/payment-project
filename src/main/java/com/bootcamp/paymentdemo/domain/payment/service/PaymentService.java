package com.bootcamp.paymentdemo.domain.payment.service;

import com.bootcamp.paymentdemo.domain.order.entity.Order;
import com.bootcamp.paymentdemo.domain.order.repository.OrderRepository;
import com.bootcamp.paymentdemo.domain.payment.dto.Request.PaymentCreateReadyRequest;
import com.bootcamp.paymentdemo.domain.payment.dto.Request.PortOneWebhookRequest;
import com.bootcamp.paymentdemo.domain.payment.dto.Response.*;
import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentRepository;
import com.bootcamp.paymentdemo.domain.refund.enums.CancelFlow;
import com.bootcamp.paymentdemo.global.error.PortOneApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PortOneApiClient portOneApiClient;
    private final PaymentRetryService paymentRetryService;
    private final PaymentLifecycleService paymentLifecycleService;
    private final PaymentAccessValidator paymentAccessValidator;

    // 결제 ID 에 넣을 날짜 포맷
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 결제 시도(Attempt) 생성
     * 프론트에서 /checkout-ready를 호출하면 여기로 들어옵니다.
     * 이 단계에서는 "결제 확정"이 아니라, 결제를 시작하기 위한 준비 레코드만 생성합니다.
     */
    @Transactional
    public PaymentCreateReadyResponse create(Authentication authentication, PaymentCreateReadyRequest request) {
        paymentAccessValidator.validateAuthenticated(authentication); // 사용자확인

        if (request.totalAmount() == null || request.totalAmount() <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }

        Order order = orderRepository.findByOrderId(request.orderId()).orElseThrow(
                () -> new IllegalArgumentException("없는 주문번호")
        );

        Long expectedAmount = order.getTotalAmount().longValue();
        if (!expectedAmount.equals(request.totalAmount())) {
            throw new IllegalArgumentException("주문 금액과 결제 금액은 같아야 합니다.");
        }

        paymentAccessValidator.validateOrderOwnership(authentication, order); // 주문한유저가 맞는지확인

        String paymentId = generatePaymentId();  // 결제고유ID 발급
        Payment payment = Payment.of(order, expectedAmount, paymentId); // 페이먼트객체생성
        paymentRepository.save(payment);

        return PaymentCreateReadyResponse.checkoutReady(payment);
    }

    /**
     * 결제 확정
     * 흐름:
     * 1) paymentId로 결제 시도 건을 비관적 락으로 조회 (동시성/중복 방지)
     * 2) 이미 처리된 건이면 멱등 응답으로 즉시 반환
     * 3) PortOne 결제 단건조회로 실제 상태/금액 검증
     * 4) 성공이면 PAID, 실패면 FAILED 처리
     * 5) 결과를 컨트롤러 응답 DTO로 반환
     */
    public PaymentConfirmResponse confirm(Authentication authentication, String paymentId) {
        Payment payment = paymentAccessValidator.getAuthorizedPayment(authentication, paymentId);

        // 이미 성공한건 멱등처리
        if (payment.isAlreadyProcessed()) {
            log.info("멱등 처리 - 이미 처리된 결제입니다. paymentId={}, status={}",
                    paymentId, payment.getStatus());
            return PaymentConfirmResponse.alreadyProcessed(payment);
        }

        PortOnePaymentInfoResponse portOnePayment; // 포트원조회에서 받을 바디
        String verifyIdempotencyKey = portOneApiClient.buildVerifyIdempotencyKey(paymentId); //멱등키 발급
        try {
            portOnePayment = portOneApiClient.getPaymentInfo(paymentId, verifyIdempotencyKey); // 결제조회로직
        } catch (PortOneApiException apiException) { // 결제조회 실패
            if (apiException.isRetryable()) {
                paymentRetryService.enqueueVerifyRetry(paymentId, verifyIdempotencyKey);
                return PaymentConfirmResponse.failed(
                        payment,
                        "포트원 조회 일시 장애로 결제 확인 재시도를 등록했습니다. reason=" + apiException.getMessage()
                );
            }
            paymentLifecycleService.markFailed(paymentId); //  결제조회실패 재시도필요없는이유
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
        } else if (portOnePayment.isTerminalFailureStatus()) { // 결제 실패
            paymentLifecycleService.markFailed(paymentId);
            return PaymentConfirmResponse.failed(
                    paymentLifecycleService.getPayment(paymentId),
                    "포트원 결제 실패. status=" + portOnePayment.getStatus()
                            + ", reason=" + portOnePayment.resolveFailureReason()
            );
        } else {
            paymentRetryService.enqueueVerifyRetry(paymentId, verifyIdempotencyKey);  // 상태 레디등 포트원 조회오류 재시도 큐등록
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
        if (request == null || request.getData() == null || request.getData().getPaymentId() == null) {
            throw new IllegalArgumentException("웹훅 요청 형식이 올바르지 않습니다. paymentId가 없습니다.");
        }

        String paymentId = request.getData().getPaymentId();
        String verifyIdempotencyKey = portOneApiClient.buildVerifyIdempotencyKey(paymentId);
        log.info("웹훅 처리 시작 - webhookId={}, paymentId={}, eventType={}",
                webhookId, paymentId, request.getType());

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
                paymentRetryService.enqueueVerifyRetry(paymentId, verifyIdempotencyKey);
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
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String random = UUID.randomUUID().toString().replace("-", "");
        return "pay-" + timestamp + "-" + random;
    }

    // 주문조회시 보여줄 결제조회 단 프론트엔드 미구현으로 안씀
    public PaymentSummaryResponse getPaymentSummary(Authentication authentication, String paymentId) {
        Payment payment = paymentAccessValidator.getAuthorizedPayment(authentication, paymentId);
        return PaymentSummaryResponse.from(payment);
    }

    /**
     * 결제 상세 조회 (PortOne 기준)
     * - paymentId를 받아 PortOne 단건조회 결과를 우리 DTO로 변환해 반환합니다.
     * - 주문/환불/관리 화면은 이 메서드만 호출해서 재사용하면 됩니다.
     *  단 프론트엔드 미구현은로 안씀
     */
    @Transactional(readOnly = true)
    public PaymentDetailResponse getPaymentDetail(Authentication authentication, String paymentId) {
        paymentAccessValidator.getAuthorizedPayment(authentication, paymentId);

        String idempotencyKey = portOneApiClient.buildVerifyIdempotencyKey(paymentId);
        PortOnePaymentInfoResponse info = portOneApiClient.getPaymentInfo(paymentId, idempotencyKey);
        return PaymentDetailResponse.from(info);
    }
}
