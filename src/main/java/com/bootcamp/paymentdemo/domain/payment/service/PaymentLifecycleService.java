package com.bootcamp.paymentdemo.domain.payment.service;

import com.bootcamp.paymentdemo.config.PortOneProperties;
import com.bootcamp.paymentdemo.domain.customer.service.MembershipService;
import com.bootcamp.paymentdemo.domain.order.service.OrderService;
import com.bootcamp.paymentdemo.domain.payment.dto.Response.PortOnePaymentInfoResponse;
import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentRepository;
import com.bootcamp.paymentdemo.domain.point.service.PointTransactionService;
import com.bootcamp.paymentdemo.domain.product.service.ProductService;
import com.bootcamp.paymentdemo.domain.refund.dto.RefundCalculation;
import com.bootcamp.paymentdemo.domain.refund.entity.Refund;
import com.bootcamp.paymentdemo.domain.refund.enums.CancelFlow;
import com.bootcamp.paymentdemo.domain.refund.repository.RefundRepository;
import com.bootcamp.paymentdemo.global.error.PortOneApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentLifecycleService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PortOneApiClient portOneApiClient;
    private final PortOneProperties portOneProperties;
    private final PaymentRetryTaskService paymentRetryTaskService;
    private final OrderService orderService;
    private final ProductService productService;
    private final PointTransactionService pointTransactionService;
    private final MembershipService membershipService;

    // 결제 객체 조회(락 x)
    @Transactional(readOnly = true)
    public Payment getPayment(String paymentId) {
        return paymentRepository.findByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );
    }

    // 결제 실패 상태 반영(락 o)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String paymentId) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );

        if (!payment.isAlreadyProcessed()) {
            payment.fail();
        }
    }

    // PortOne 응답 금액/상점 ID를 우리 결제 정보와 대조
    public void validateApprovedPayment(Payment payment, PortOnePaymentInfoResponse portOnePayment) {
        Long paidAmount = portOnePayment.resolveTotalAmount();
        Long expectedAmount = payment.getPgAmount();

        if (paidAmount == null || !paidAmount.equals(expectedAmount)) {
            throw new IllegalStateException(
                    "결제 금액 불일치. expected=" + expectedAmount + ", actual=" + paidAmount
            );
        }

        String expectedStoreId = portOneProperties.getStore().getId();
        String actualStoreId = portOnePayment.getStoreId();
        if (expectedStoreId != null && actualStoreId != null && !expectedStoreId.equals(actualStoreId)) {
            throw new IllegalStateException(
                    "상점 ID 불일치. expected=" + expectedStoreId + ", actual=" + actualStoreId
            );
        }
    }

    // PortOne 결제 성공 후 내부 완료 처리
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment completeApprovedPayment(String paymentId, PortOnePaymentInfoResponse portOnePayment) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );

        if (payment.isAlreadyProcessed()) {
            return payment;
        }

        validateApprovedPayment(payment, portOnePayment);
        completePaymentInternal(payment);
        return payment;
    }

    // PortOne 호출이 없는 0원 결제 완료 처리
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment completePointOnlyPayment(String paymentId) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );

        if (payment.isAlreadyProcessed()) {
            return payment;
        }

        if (payment.getPgAmount() != 0L) {
            throw new IllegalStateException("0원 결제 전용 완료 처리입니다. paymentId=" + paymentId);
        }

        completePaymentInternal(payment);
        return payment;
    }


    // 결제 확정 후 내부 처리 실패 시 타는 보상 취소 진입점
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String compensateApprovedPayment(String paymentId, String reason) {
        return cancelApprovedPayment(paymentId, reason, CancelFlow.COMPENSATION, null);
    }

    // 환불/보상취소 공통 진입점
    // - 환불이면 refundCalculation에 부분취소 금액/회수 포인트가 들어온다.
    // - 보상취소면 refundCalculation은 null 이고 전액 취소로 처리한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String cancelApprovedPayment(
            String paymentId,
            String reason,
            CancelFlow cancelFlow,
            RefundCalculation refundCalculation
    ) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );

        if (payment.getPgAmount() == 0L) {
            return completePointOnlyCancel(payment, reason, cancelFlow);
        }

        String cancelIdempotencyKey = portOneApiClient.buildCancelIdempotencyKey(paymentId);

        try {
            PortOnePaymentInfoResponse cancelResult = portOneApiClient.paymentCancel(
                    paymentId,
                    reason,
                    cancelIdempotencyKey,
                    resolveCancelAmount(refundCalculation)
            );
            return handleCancelResult(payment, reason, cancelFlow, cancelIdempotencyKey, cancelResult, refundCalculation);
        } catch (PortOneApiException cancelException) {
            return handleCancelFailure(payment, reason, cancelFlow, cancelIdempotencyKey, cancelException, refundCalculation);
        } catch (Exception cancelException) {
            return handleCancelFailure(payment, reason, cancelFlow, cancelIdempotencyKey, cancelException, refundCalculation);
        }
    }

    // PortOne 취소 성공 이후, 우리 시스템의 환불/원복 후처리를 반영
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRefundedAfterCancel(String paymentId, String reason, CancelFlow cancelFlow) {
        markRefundedAfterCancel(paymentId, reason, cancelFlow, null);
    }

    // 취소 재시도 성공 후에도 같은 후처리를 재사용할 수 있게 오버로드를 둔다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRefundedAfterCancel(
            String paymentId,
            String reason,
            CancelFlow cancelFlow,
            RefundCalculation refundCalculation
    ) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );

        applyCancelSuccess(payment, reason, cancelFlow, refundCalculation);
    }

    // 환불 성공 후 내부 원복 로직
    // - 환불은 환불 시작 시 계산한 recoverableEarnedPoints를 그대로 사용한다.
    // - 보상취소는 내부 후속 작업이 이미 롤백된 전제라 결제/환불 상태만 맞춘다.
    private void applyCancelSuccess(
            Payment payment,
            String reason,
            CancelFlow cancelFlow,
            RefundCalculation refundCalculation
    ) {
        payment.refund();

        if (cancelFlow == CancelFlow.REFUND) {
            markExistingRefundRefunded(payment);
            if (payment.getUsePoint() > 0L) {
                pointTransactionService.refundUsedPoints(payment.getOrder().getId());
            }
            long recoverableAmount = resolveRecoverableEarnedPoints(refundCalculation);
            if (payment.getPgAmount() > 0L && recoverableAmount > 0L) {
                pointTransactionService.cancelEarnedPoints(
                        payment.getOrder().getId(), recoverableAmount);
            }
            productService.restoreStockByOrder(payment.getOrder().getId());
            orderService.cancelOrder(payment.getOrder().getId());
            membershipService.refreshUserMembership(payment.getOrder().getCustomer().getId());
        } else {
            upsertRefundForCompensation(payment, reason);
            // 보상 취소는 completeApprovedPayment()의 REQUIRES_NEW 트랜잭션 롤백 이후 수행된다.
            // 따라서 주문/재고/포인트 같은 내부 후속 작업은 이미 롤백되었다는 전제로,
            // 여기서는 다른 도메인 원복을 다시 호출하지 않고 결제/환불 상태 정합성만 맞춘다.
        }
    }

    // 보상 취소 성공 시 환불 레코드를 보정
    private void upsertRefundForCompensation(Payment payment, String reason) {
        refundRepository.findByPayment(payment)
                .ifPresentOrElse(
                        Refund::markRefunded,
                        () -> refundRepository.save(Refund.createRefunded(payment, payment.getPgAmount(), reason))
                );
    }

    // 환불 성공 상태 반영
    private void markExistingRefundRefunded(Payment payment) {
        Refund refund = refundRepository.findByPayment(payment).orElseThrow(
                () -> new IllegalStateException("환불 요청 레코드가 없습니다. paymentId=" + payment.getPaymentId())
        );
        refund.markRefunded();
        refundRepository.save(refund);
    }

    // 환불 재시도 상태 반영
    private void markExistingRefundRetrying(Payment payment) {
        Refund refund = refundRepository.findByPayment(payment).orElseThrow(
                () -> new IllegalStateException("환불 요청 레코드가 없습니다. paymentId=" + payment.getPaymentId())
        );
        refund.markRetrying();
        refundRepository.save(refund);
    }

    // PortOne 취소 결과가 최종 취소 상태인지 확인
    private boolean isCancelledStatus(String status) {
        if (status == null) {
            return false;
        }
        return "CANCELLED".equalsIgnoreCase(status) || "PARTIAL_CANCELLED".equalsIgnoreCase(status);
    }

    // 환불 재시도 최종 실패 상태 반영
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRefundFailed(String paymentId) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );
        Refund refund = refundRepository.findByPayment(payment).orElseThrow(
                () -> new IllegalStateException("환불 요청 레코드가 없습니다. paymentId=" + paymentId)
        );
        refund.markFailed();
        refundRepository.save(refund);
    }

    // 결제 확정 후 주문/재고/포인트/멤버십 후처리
    private void completePaymentInternal(Payment payment) {
        Long orderId = payment.getOrder().getId();
        Long customerId = payment.getOrder().getCustomer().getId();

        // 사용 포인트가 있으면 결제 확정 시점에 차감한다.
        if (payment.getUsePoint() > 0L) {
            pointTransactionService.usePoints(customerId, payment.getUsePoint(), payment.getOrder().getId());
        }

        orderService.completeOrder(orderId);
        productService.decreaseStockByOrder(orderId);

        // 적립과 멤버십 누적은 실제 PG 결제 금액이 있을 때만 반영한다.
        if (payment.getPgAmount() > 0L) {
            pointTransactionService.earnPointAfterPayment(customerId, orderId, payment.getPgAmount());
            membershipService.updateMembershipAfterPayment(customerId, payment.getPgAmount());
        }

        payment.confirm();
        log.info("결제 내부 처리 완료 - paymentId={}, orderId={}", payment.getPaymentId(), payment.getOrder().getOrderId());
    }

    // 0원 결제는 PortOne 취소 없이 내부 환불 후처리만 수행한다.
    private String completePointOnlyCancel(Payment payment, String reason, CancelFlow cancelFlow) {
        applyCancelSuccess(payment, reason, cancelFlow, null);
        if (cancelFlow == CancelFlow.COMPENSATION) {
            return "포인트 전액 결제 보상 취소 성공";
        }
        return "포인트 전액 결제 환불 성공";
    }

    // PortOne 취소 성공/미확정 응답에 따라 후처리 또는 재시도 등록을 결정한다.
    private String handleCancelResult(
            Payment payment,
            String reason,
            CancelFlow cancelFlow,
            String cancelIdempotencyKey,
            PortOnePaymentInfoResponse cancelResult,
            RefundCalculation refundCalculation
    ) {
        String cancelStatus = cancelResult.getStatus();
        if (isCancelledStatus(cancelStatus)) {
            applyCancelSuccess(payment, reason, cancelFlow, refundCalculation);
            if (cancelFlow == CancelFlow.COMPENSATION) {
                return "보상 취소 성공. cancelStatus=" + cancelStatus;
            }
            return "환불 성공. cancelStatus=" + cancelStatus;
        }

        paymentRetryTaskService.enqueueCancelRetry(
                payment.getPaymentId(),
                cancelIdempotencyKey,
                reason,
                cancelFlow,
                refundCalculation
        );
        if (cancelFlow == CancelFlow.COMPENSATION) {
            return "보상 취소 응답 확인 필요(재시도 등록). cancelStatus=" + cancelStatus;
        }

        markExistingRefundRetrying(payment);
        return "환불 처리 미확정(재시도 등록). cancelStatus=" + cancelStatus;
    }

    // PortOne 취소 호출 자체가 실패하면 재시도 큐 등록 후 현재 상태를 반환한다.
    private String handleCancelFailure(
            Payment payment,
            String reason,
            CancelFlow cancelFlow,
            String cancelIdempotencyKey,
            Exception cancelException,
            RefundCalculation refundCalculation
    ) {
        paymentRetryTaskService.enqueueCancelRetry(
                payment.getPaymentId(),
                cancelIdempotencyKey,
                reason,
                cancelFlow,
                refundCalculation
        );

        if (cancelFlow == CancelFlow.COMPENSATION) {
            log.error("보상 취소 실패 - paymentId={}, message={}",
                    payment.getPaymentId(), cancelException.getMessage(), cancelException);
            return "보상 취소 실패(재시도 등록): " + cancelException.getMessage();
        }

        markExistingRefundRetrying(payment);
        log.error("환불 실패 - paymentId={}, message={}",
                payment.getPaymentId(), cancelException.getMessage(), cancelException);
        return "환불 실패(재시도 등록): " + cancelException.getMessage();
    }

    // 환불 계산값이 있으면 그 금액으로 부분취소하고, 없으면 전액 취소(null)로 둔다.
    private Long resolveCancelAmount(RefundCalculation refundCalculation) {
        if (refundCalculation == null) {
            return null;
        }
        return refundCalculation.cancelAmount();
    }

    // 실제 환불 후처리에서는 null 이면 0으로 보고 적립 포인트 회수를 건너뛴다.
    private long resolveRecoverableEarnedPoints(RefundCalculation refundCalculation) {
        if (refundCalculation == null || refundCalculation.recoverableEarnedPoints() == null) {
            return 0L;
        }
        return refundCalculation.recoverableEarnedPoints();
    }
}
