package com.bootcamp.paymentdemo.domain.payment.service;

import com.bootcamp.paymentdemo.config.PortOneProperties;
import com.bootcamp.paymentdemo.domain.order.entity.Order;
import com.bootcamp.paymentdemo.domain.order.repository.OrderRepository;
import com.bootcamp.paymentdemo.domain.payment.dto.Request.PaymentCreateReadyRequest;
import com.bootcamp.paymentdemo.domain.payment.dto.Request.PortOneWebhookRequest;
import com.bootcamp.paymentdemo.domain.payment.dto.Response.PaymentConfirmResponse;
import com.bootcamp.paymentdemo.domain.payment.dto.Response.PaymentCreateReadyResponse;
import com.bootcamp.paymentdemo.domain.payment.dto.Response.PortOnePaymentInfoResponse;
import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentRepository;
import com.bootcamp.paymentdemo.domain.user.entity.User;
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
    private final PortOneProperties portOneProperties;
    private final PortOneApiClient portOneApiClient;

    // 결제 ID 에 넣을 날짜 포맷
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 결제 시도(Attempt) 생성
     * 프론트에서 /checkout-ready를 호출하면 여기로 들어옵니다.
     * 이 단계에서는 "결제 확정"이 아니라, 결제를 시작하기 위한 준비 레코드만 생성합니다.
     */
    @Transactional
    public PaymentCreateReadyResponse create(Authentication authentication, PaymentCreateReadyRequest request) {
        User user = extractAuthenticatedUser(authentication);

        if (request.totalAmount() == null || request.totalAmount() <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }

        Order order = orderRepository.findById(request.orderId()).orElseThrow(
                () -> new IllegalArgumentException("없는 주문번호")
        );

        String paymentId = generatePaymentId();
        Payment payment = Payment.of(order, request.totalAmount(), paymentId);
        paymentRepository.save(payment);

        PaymentCreateReadyResponse.Customer customer = PaymentCreateReadyResponse.Customer.of(
                String.valueOf(user.getId()),
                user.getName(),
                user.getPhone(),
                user.getEmail()
        );

        return PaymentCreateReadyResponse.checkoutReady(
                portOneProperties.getStore().getId(),
                resolveKgInicisChannelKey(),
                payment.getPaymentId(),
                order.getOrderNumber(),
                payment.getAmount(),
                customer
        );
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
    @Transactional
    public PaymentConfirmResponse confirm(String paymentId) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );

        // 멱등성 처리: 이미 성공/실패/환불된 결제는 재처리하지 않고 현재 상태 그대로 반환
        if (payment.isAlreadyProcessed()) {
            log.info("멱등 처리 - 이미 처리된 결제입니다. paymentId={}, status={}",
                    paymentId, payment.getStatus());
            return PaymentConfirmResponse.alreadyProcessed(
                    payment.getOrder().getId(),
                    payment.getPaymentId(),
                    payment.getStatus().name()
            );
        }

        // PortOne을 단일 진실원천(Source of Truth)으로 삼아 실제 결제 상태를 확인
        PortOnePaymentInfoResponse portOnePayment = portOneApiClient.getPaymentInfo(paymentId);

        // 1) 결제 상태 검증
        if (!portOnePayment.isPaidStatus()) {
            payment.fail();
            return PaymentConfirmResponse.failed(
                    payment.getOrder().getId(),
                    payment.getPaymentId(),
                    payment.getStatus().name(),
                    "포트원 결제 상태가 PAID가 아닙니다. status=" + portOnePayment.getStatus()
            );
        }

        // 2) 결제 금액 검증 (위변조 방지)
        Long paidAmount = portOnePayment.resolveTotalAmount();
        if (paidAmount == null || !paidAmount.equals(payment.getAmount())) {
            payment.fail();
            return PaymentConfirmResponse.failed(
                    payment.getOrder().getId(),
                    payment.getPaymentId(),
                    payment.getStatus().name(),
                    "결제 금액 불일치. expected=" + payment.getAmount() + ", actual=" + paidAmount
            );
        }

        // 3) 상점 ID 검증 (선택 사항이지만 안전성 향상에 도움이 됨)
        String expectedStoreId = portOneProperties.getStore().getId();
        String actualStoreId = portOnePayment.getStoreId();
        if (expectedStoreId != null && actualStoreId != null && !expectedStoreId.equals(actualStoreId)) {
            payment.fail();
            return PaymentConfirmResponse.failed(
                    payment.getOrder().getId(),
                    payment.getPaymentId(),
                    payment.getStatus().name(),
                    "상점 ID 불일치. expected=" + expectedStoreId + ", actual=" + actualStoreId
            );
        }

        // 위 검증을 모두 통과한 경우에만 결제 확정
        payment.confirm();

        // TODO(미구현 훅): 결제 성공 이후 주문 상태 완료 처리 호출
        // orderService.completeOrder(payment.getOrder().getId());
        //
        // TODO(미구현 훅): 결제 성공 이후 재고 차감 호출
        // inventoryService.decreaseStockByOrder(payment.getOrder().getId());
        //
        // TODO(미구현 훅): 결제 성공 이후 포인트 적립 호출
        // pointService.earnPoints(payment.getOrder().getId());

        return PaymentConfirmResponse.success(
                payment.getOrder().getId(),
                payment.getPaymentId(),
                payment.getStatus().name()
        );
    }

    /**
     * 포트원 웹훅 처리
     * Webhook은 Client Confirm과 동일한 "결제 확정 검증 규칙"을 따라야 합니다.
     * (상태/금액/상점 검증, 멱등성 보장)
     *
     * 컨트롤러에서 IllegalArgumentException을 분기 처리하고 있으므로,
     * 비즈니스적으로 거절해야 하는 상황은 IllegalArgumentException으로 올립니다.
     */
    @Transactional
    public void processWebhook(String webhookId, PortOneWebhookRequest request) {
        if (request == null || request.getData() == null || request.getData().getPaymentId() == null) {
            throw new IllegalArgumentException("웹훅 요청 형식이 올바르지 않습니다. paymentId가 없습니다.");
        }

        String paymentId = request.getData().getPaymentId();
        log.info("웹훅 처리 시작 - webhookId={}, paymentId={}, eventType={}",
                webhookId, paymentId, request.getType());

        // 1) 같은 결제건 동시 처리 방지를 위해 비관적 락으로 조회
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("존재하지 않는 결제 건입니다. paymentId=" + paymentId)
        );

        // 2) 멱등성 처리: 이미 처리된 결제는 그대로 종료
        if (payment.isAlreadyProcessed()) {
            log.info("웹훅 멱등 처리 - 이미 처리된 결제입니다. paymentId={}, status={}",
                    paymentId, payment.getStatus());
            return;
        }

        // 3) 포트원 단건조회로 실제 결제 상태 확인
        PortOnePaymentInfoResponse portOnePayment = portOneApiClient.getPaymentInfo(paymentId);

        // 4) 상태 검증
        if (!portOnePayment.isPaidStatus()) {
            payment.fail();
            throw new IllegalArgumentException("웹훅 검증 실패: 결제 상태가 PAID가 아닙니다. status="
                    + portOnePayment.getStatus());
        }

        // 5) 금액 검증
        Long paidAmount = portOnePayment.resolveTotalAmount();
        if (paidAmount == null || !paidAmount.equals(payment.getAmount())) {
            payment.fail();
            throw new IllegalArgumentException("웹훅 검증 실패: 결제 금액 불일치. expected="
                    + payment.getAmount() + ", actual=" + paidAmount);
        }

        // 6) 상점 ID 검증
        String expectedStoreId = portOneProperties.getStore().getId();
        String actualStoreId = portOnePayment.getStoreId();
        if (expectedStoreId != null && actualStoreId != null && !expectedStoreId.equals(actualStoreId)) {
            payment.fail();
            throw new IllegalArgumentException("웹훅 검증 실패: 상점 ID 불일치. expected="
                    + expectedStoreId + ", actual=" + actualStoreId);
        }

        // 7) 검증 통과 -> 결제 확정
        payment.confirm();

        // TODO(미구현 훅): 주문 완료 처리 연결
        // orderService.completeOrder(payment.getOrder().getId());

        // TODO(미구현 훅): 재고 차감 처리 연결
        // inventoryService.decreaseStockByOrder(payment.getOrder().getId());

        // TODO(미구현 훅): 포인트 적립 처리 연결
        // pointService.earnPoints(payment.getOrder().getId());

        log.info("웹훅 처리 완료 - paymentId={}, finalStatus={}", paymentId, payment.getStatus());
    }

    /**
     * 결제 ID 생성기
     */
    public static String generatePaymentId() {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String random = UUID.randomUUID().toString().replace("-", "");
        return "pay-" + timestamp + "-" + random;
    }

    private String resolveKgInicisChannelKey() {
        if (portOneProperties.getChannel() == null) {
            return null;
        }
        return portOneProperties.getChannel().get("kg-inicis");
    }

    private User extractAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("인증된 사용자만 결제를 요청할 수 있습니다.");
        }

        Object principal = authentication.getPrincipal();
        if ("anonymousUser".equals(principal)) {
            throw new IllegalStateException("인증된 사용자만 결제를 요청할 수 있습니다.");
        }
        if (!(principal instanceof User user)) {
            throw new IllegalStateException("인증 사용자 타입이 올바르지 않습니다.");
        }
        return user;
    }
}
