package com.bootcamp.paymentdemo.domain.payment.service;

import com.bootcamp.paymentdemo.config.PortOneProperties;
import com.bootcamp.paymentdemo.domain.customer.service.MembershipService;
import com.bootcamp.paymentdemo.domain.order.service.OrderService;
import com.bootcamp.paymentdemo.domain.payment.dto.Response.PortOnePaymentInfoResponse;
import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.payment.entity.PaymentRetryTask;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentRetryOperation;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentRetryStatus;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentStatus;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentRepository;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentRetryTaskRepository;
import com.bootcamp.paymentdemo.domain.point.service.PointTransactionService;
import com.bootcamp.paymentdemo.domain.product.service.ProductService;
import com.bootcamp.paymentdemo.domain.refund.entity.Refund;
import com.bootcamp.paymentdemo.domain.refund.enums.CancelFlow;
import com.bootcamp.paymentdemo.domain.refund.repository.RefundRepository;
import com.bootcamp.paymentdemo.global.error.PortOneApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentLifecycleService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PortOneApiClient portOneApiClient;
    private final PortOneProperties portOneProperties;
    private final PaymentRetryTaskRepository paymentRetryTaskRepository;
    private final OrderService orderService;
    private final ProductService productService;
    private final PointTransactionService pointTransactionService;
    private final MembershipService membershipService;

    // 페미언트 객제조회(락 x)
    @Transactional(readOnly = true)
    public Payment getPayment(String paymentId) {
        return paymentRepository.findByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );
    }

    //페이먼트 객체조회(락 o)
    @Transactional(propagation = Propagation.REQUIRES_NEW) // 새로운트랜잭션
    public void markFailed(String paymentId) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );

        if (!payment.isAlreadyProcessed()) {
            payment.fail();
        }
    }

    // 포트원결제금액과  페이먼트 결제금액검증 상점ID검증
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

    //결제완료 로직
    @Transactional(propagation = Propagation.REQUIRES_NEW) // 새로운트랜젝션
    public Payment completeApprovedPayment(String paymentId, PortOnePaymentInfoResponse portOnePayment) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId).orElseThrow(  // 락걸기
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );

        if (payment.isAlreadyProcessed()) { // 이미처리된건이면 그냥돌려보내
            return payment;
        }

        validateApprovedPayment(payment, portOnePayment);  // 포트원결제금액과 DB결제금액 검증 상점ID검증
        completePaymentInternal(payment); // 결제완료처리
        return payment;
    }

    // 모든금액 포인트로 결제했을경우 완료로직
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment completePointOnlyPayment(String paymentId) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );

        if (payment.isAlreadyProcessed()) { // 멱등처리
            return payment;
        }

        if (payment.getPgAmount() != 0L) {
            throw new IllegalStateException("0원 결제 전용 완료 처리입니다. paymentId=" + paymentId);
        }

        completePaymentInternal(payment); // 결제완료처리
        return payment;
    }


    // 결제취소 자동생성 로직
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String compensateApprovedPayment(String paymentId, String reason) {
        return cancelApprovedPayment(paymentId, reason, CancelFlow.COMPENSATION); // 결제취소로직
    }

    // 결제취소, 보상트랜젝션 로직
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String cancelApprovedPayment(String paymentId, String reason, CancelFlow cancelFlow) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId).orElseThrow( // 락걸기
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );

        if (payment.getPgAmount() == 0L) {
            applyCancelSuccess(payment, reason, cancelFlow);
            if (cancelFlow == CancelFlow.COMPENSATION) {
                return "포인트 전액 결제 보상 취소 성공";
            }
            return "포인트 전액 결제 환불 성공";
        }

        String cancelIdempotencyKey = portOneApiClient.buildCancelIdempotencyKey(paymentId); // 멱등키 생성

        try {
            PortOnePaymentInfoResponse cancelResult = portOneApiClient.paymentCancel( // 포트원 결제취소
                    paymentId,
                    reason,
                    cancelIdempotencyKey
            );

            String cancelStatus = cancelResult.getStatus(); // 응답
            if (isCancelledStatus(cancelStatus)) { // 취소 성공
                applyCancelSuccess(payment, reason, cancelFlow); // 원상복구 로직
                if (cancelFlow == CancelFlow.COMPENSATION) {
                    return "보상 취소 성공. cancelStatus=" + cancelStatus;
                }
                return "환불 성공. cancelStatus=" + cancelStatus;
            }

            enqueueCancelRetry(paymentId, cancelIdempotencyKey, reason, cancelFlow); // 취소 실패 재시도 큐등록

            if (cancelFlow == CancelFlow.COMPENSATION) {
                return "보상 취소 응답 확인 필요(재시도 등록). cancelStatus=" + cancelStatus;
            }

            markExistingRefundRetrying(payment);
            return "환불 처리 미확정(재시도 등록). cancelStatus=" + cancelStatus;
        } catch (PortOneApiException cancelException) {  // 호출 자체가 실패
            enqueueCancelRetry(paymentId, cancelIdempotencyKey, reason, cancelFlow); // 취소 실패 재시도 큐등록

            if (cancelFlow == CancelFlow.COMPENSATION) {
                log.error("보상 취소 실패 - paymentId={}, retryable={}, message={}",
                        paymentId, cancelException.isRetryable(), cancelException.getMessage(), cancelException);
                return "보상 취소 실패(재시도 등록): " + cancelException.getMessage();
            }

            markExistingRefundRetrying(payment);
            log.error("환불 실패 - paymentId={}, retryable={}, message={}",
                    paymentId, cancelException.isRetryable(), cancelException.getMessage(), cancelException);
            return "환불 실패(재시도 등록): " + cancelException.getMessage();
        } catch (Exception cancelException) {  // 그외 이유로 실패
            enqueueCancelRetry(paymentId, cancelIdempotencyKey, reason, cancelFlow); // 취소 실패 재시도 큐등록

            if (cancelFlow == CancelFlow.COMPENSATION) {
                log.error("보상 취소 실패(기타) - paymentId={}, message={}",
                        paymentId, cancelException.getMessage(), cancelException);
                return "보상 취소 실패(재시도 등록): " + cancelException.getMessage();
            }

            markExistingRefundRetrying(payment);
            log.error("환불 실패(기타) - paymentId={}, message={}",
                    paymentId, cancelException.getMessage(), cancelException);
            return "환불 실패(재시도 등록): " + cancelException.getMessage();
        }
    }

    // 결제취소, 보상트랜젝션 재시도 로직
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRefundedAfterCancel(String paymentId, String reason, CancelFlow cancelFlow) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId).orElseThrow( //락걸기
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );

        applyCancelSuccess(payment, reason, cancelFlow); // 원상복구 로직
    }

    // 원상복구 로직
    private void applyCancelSuccess(Payment payment, String reason, CancelFlow cancelFlow) {
        payment.refund();

        if (cancelFlow == CancelFlow.REFUND) {
            markExistingRefundRefunded(payment); // 환불성공상태변경
            if (payment.getUsePoint() > 0L) {
                pointTransactionService.refundUsedPoints(payment.getOrder().getOrderId()); // 포인트복구
            }
            if (payment.getPgAmount() > 0L) {
                pointTransactionService.cancelEarnedPoints(String.valueOf(payment.getOrder().getId())); // 포인트 회수
            }
            productService.restoreStockByOrder(payment.getOrder().getId()); // 재고복구
            orderService.cancelOrder(payment.getOrder().getId());  // 주문상태변경
            membershipService.refreshUserMembership(payment.getOrder().getCustomer().getId()); // 멤버쉽 등급재확인
        } else {
            upsertRefundForCompensation(payment, reason);
            // 보상 취소는 completeApprovedPayment()의 REQUIRES_NEW 트랜잭션 롤백 이후 수행된다.
            // 따라서 주문/재고/포인트 같은 내부 후속 작업은 이미 롤백되었다는 전제로,
            // 여기서는 다른 도메인 원복을 다시 호출하지 않고 결제/환불 상태 정합성만 맞춘다.
        }
    }

    // 보상트랜젝션에서 결제취소 성공
    private void upsertRefundForCompensation(Payment payment, String reason) {
        refundRepository.findByPayment(payment) //환불이 이미있다면 환불성공으로해주고 없다면 환불만들어서 성공으로 남겨주기
                .ifPresentOrElse(
                        Refund::markRefunded,
                        () -> refundRepository.save(Refund.createRefunded(payment, payment.getPgAmount(), reason))
                );
    }

    // 환불성공 상태변경
    private void markExistingRefundRefunded(Payment payment) {
        Refund refund = refundRepository.findByPayment(payment).orElseThrow(
                () -> new IllegalStateException("환불 요청 레코드가 없습니다. paymentId=" + payment.getPaymentId())
        );
        refund.markRefunded(); //환불성공 상태변경
    }

    // 환불실패 상태변경
    private void markExistingRefundRetrying(Payment payment) {
        Refund refund = refundRepository.findByPayment(payment).orElseThrow(
                () -> new IllegalStateException("환불 요청 레코드가 없습니다. paymentId=" + payment.getPaymentId())
        );
        refund.markRetrying(); // 재시도중 상태변경
    }

    // 환불, 결제취소 검증
    private boolean isCancelledStatus(String status) {
        if (status == null) {
            return false;
        }
        return "CANCELLED".equalsIgnoreCase(status) || "PARTIAL_CANCELLED".equalsIgnoreCase(status);
    }

    // 환불 재시도후 최종실패
    public void markRefundFailed(String paymentId) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );
        Refund refund = refundRepository.findByPayment(payment).orElseThrow(
                () -> new IllegalStateException("환불 요청 레코드가 없습니다. paymentId=" + paymentId)
        );
        refund.markFailed(); //실패로 상태변경
    }

    // 취소 재시도 큐등록
    private void enqueueCancelRetry(String paymentId, String idempotencyKey, String reason, CancelFlow cancelFlow) {
        boolean alreadyExists = paymentRetryTaskRepository.existsByPaymentIdAndOperationAndStatusIn( // 이미 테스크등록되었는지 찾기
                paymentId,
                PaymentRetryOperation.CANCEL_PAYMENT,
                Set.of(PaymentRetryStatus.PENDING, PaymentRetryStatus.PROCESSING)
        );
        if (alreadyExists) { // 등록되었다면 돌려보낸다
            return;
        }
        paymentRetryTaskRepository.save(PaymentRetryTask.cancelTask(paymentId, idempotencyKey, reason, cancelFlow)); // 테스크등록
    }

    // 결제확정후로직
    private void completePaymentInternal(Payment payment) {
        Long orderId = payment.getOrder().getId();
        Long customerId = payment.getOrder().getCustomer().getId();

        //포인트사용
        if (payment.getUsePoint() > 0L) {
            pointTransactionService.usePoints(customerId, payment.getUsePoint(), payment.getOrder().getOrderId());
        }

        orderService.completeOrder(orderId); // 주문상태변경
        productService.decreaseStockByOrder(orderId); // 물품재고수량변경

        // 결제후 포인트적립 & 멤버쉽 업데이트
        if (payment.getPgAmount() > 0L) {
            pointTransactionService.earnPointAfterPayment(customerId, orderId, payment.getPgAmount());
            membershipService.updateMembershipAfterPayment(customerId, payment.getPgAmount());
        }

        payment.confirm();
        log.info("결제 내부 처리 완료 - paymentId={}, orderId={}", payment.getPaymentId(), payment.getOrder().getOrderId());
    }
}
