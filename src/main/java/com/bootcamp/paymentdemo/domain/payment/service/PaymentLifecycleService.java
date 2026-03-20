package com.bootcamp.paymentdemo.domain.payment.service;

import com.bootcamp.paymentdemo.config.PortOneProperties;
import com.bootcamp.paymentdemo.domain.payment.dto.Response.PortOnePaymentInfoResponse;
import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.payment.entity.PaymentRetryTask;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentRetryOperation;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentRetryStatus;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentStatus;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentRepository;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentRetryTaskRepository;
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

    // 포트원결제금액과 DB결제금액 검증 상점ID검증
    public void validateApprovedPayment(Payment payment, PortOnePaymentInfoResponse portOnePayment) {
        Long paidAmount = portOnePayment.resolveTotalAmount();
        Long expectedAmount = payment.getOrder().getTotalAmount().longValue();

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
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );

        if (payment.isAlreadyProcessed()) { // 이미처리된건이면 그냥돌려보내
            return payment;
        }

        validateApprovedPayment(payment, portOnePayment);  // 포트원결제금액과 DB결제금액 검증 상점ID검증

        // TODO: 주문 도메인 팀 구현 필요
        // - 입력값: payment.getOrder().getId() 또는 payment.getOrder().getOrderId()
        // - 책임: 주문 상태를 PENDING -> PAID 로 변경
        // - 규칙:
        //   1) 현재 주문이 PENDING 일 때만 완료 처리
        //   2) 이미 PAID 라면 멱등 처리(재호출이어도 예외 없이 종료)
        //   3) CANCELLED 등 완료 불가 상태라면 예외를 던져 상위 결제 트랜잭션이 실패를 감지할 수 있어야 함
        // - 주의: 예외를 내부에서 삼키지 말고 밖으로 전파해야 보상 취소로 이어질 수 있음
        // orderService.completeOrder(payment.getOrder().getId());

        // TODO: 상품/재고 도메인 팀 구현 필요
        // - 입력값: payment.getOrder().getId()
        // - 책임: 주문상품 목록 기준으로 각 상품 재고를 주문 수량만큼 차감
        // - 규칙:
        //   1) 주문 ID로 주문상품 목록 조회
        //   2) 각 상품 재고를 quantity 만큼 차감
        //   3) 재고 부족 시 예외를 던져 전체 작업이 롤백되게 해야 함
        //   4) 한 상품이라도 실패하면 부분 차감이 남지 않도록 트랜잭션으로 묶여야 함
        // inventoryService.decreaseStockByOrder(payment.getOrder().getId());

        // TODO: 포인트 도메인 팀 구현 필요
        // - 입력값: customerId, orderPk, paidAmount
        // - 책임: 결제 성공 후 적립 포인트 계산 및 스냅샷/상세/이력 반영
        // - 규칙:
        //   1) 고객 등급/정책 기준으로 적립 포인트 계산
        //   2) 포인트 스냅샷 증가
        //   3) PointDetail / PointHistory 등 이력 저장
        //   4) 같은 주문으로 중복 적립되지 않도록 멱등 처리 필요
        // pointTransactionService.earnPointAfterPayment(
        //     payment.getOrder().getCustomer().getId(),
        //     payment.getOrder().getId(),
        //     payment.getAmount()
        // );

        // TODO: 멤버십 도메인 팀 구현 필요
        // - 입력값: customerId, paidAmount
        // - 책임: 누적 결제금액 반영 및 등급 재판정
        // - 규칙:
        //   1) 멤버십 레코드 조회
        //   2) paidAmount 를 누적 결제금액에 반영
        //   3) 기준 충족 시 등급/적립률 갱신
        //   4) 실패 시 예외를 던져 결제 상위 로직이 보상 취소로 이어갈 수 있어야 함
        // membershipService.updateMembershipAfterPayment(
        //     payment.getOrder().getCustomer().getId(),
        //     payment.getAmount()
        // );

        payment.confirm();

        log.info("결제 내부 처리 완료 - paymentId={}, orderId={}", paymentId, payment.getOrder().getOrderId());
        return payment;
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String compensateApprovedPayment(String paymentId, String reason) {
        return cancelApprovedPayment(paymentId, reason, CancelFlow.COMPENSATION);
    }

    // 결제취소, 보상트랜젝션 로직
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String cancelApprovedPayment(String paymentId, String reason, CancelFlow cancelFlow) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );
        String cancelIdempotencyKey = portOneApiClient.buildCancelIdempotencyKey(paymentId); // 멱등키 생성

        try {
            PortOnePaymentInfoResponse cancelResult = portOneApiClient.paymentCancel( // 포트원 결제취소
                    paymentId,
                    reason,
                    cancelIdempotencyKey
            );

            String cancelStatus = cancelResult.getStatus();
            if (isCancelledStatus(cancelStatus)) { // 취소 성공
                payment.refund();  // 페이먼트 상태변경

                // TODO: 주문 도메인 팀 구현 필요
                // - 입력값: payment.getOrder().getId()
                // - 책임: 환불/취소 성공 후 주문 상태를 CANCELLED(또는 환불 상태)로 변경
                // - 규칙:
                //   1) 이미 취소된 주문이면 멱등 처리
                //   2) 취소 불가 상태 정책이 있으면 예외 전파
                // orderService.cancelOrder(payment.getOrder().getId());

                // TODO: 상품/재고 도메인 팀 구현 필요
                // - 입력값: payment.getOrder().getId()
                // - 책임: 이 주문 때문에 차감했던 재고를 다시 복구
                // - 규칙:
                //   1) 주문상품 목록 기준으로 각 상품 재고 복구
                //   2) 중복 복구가 일어나지 않도록 멱등 처리 필요
                // inventoryService.restoreStockByOrder(payment.getOrder().getId());

                // TODO: 포인트 도메인 팀 구현 필요
                // - 입력값: payment.getOrder().getOrderId() 또는 order PK
                // - 책임:
                //   1) 사용 포인트가 있었다면 환원
                //   2) 이미 적립된 포인트가 있었다면 회수 또는 무효화
                // - 규칙:
                //   1) 중복 환원/중복 회수가 발생하지 않도록 멱등 처리
                //   2) 포인트 스냅샷과 이력을 함께 맞춰야 함
                // pointTransactionService.refundUsedPoints(payment.getOrder().getOrderId());


                if (cancelFlow == CancelFlow.COMPENSATION) {
                    upsertRefundForCompensation(payment, reason);
                    return "보상 취소 성공. cancelStatus=" + cancelStatus;
                }

                markExistingRefundRefunded(payment);
                return "환불 성공. cancelStatus=" + cancelStatus;
            }

            enqueueCancelRetry(paymentId, cancelIdempotencyKey, reason, cancelFlow); // 취소 실패 호출은성공

            if (cancelFlow == CancelFlow.COMPENSATION) {
                return "보상 취소 응답 확인 필요(재시도 등록). cancelStatus=" + cancelStatus;
            }

            markExistingRefundRetrying(payment);
            return "환불 처리 미확정(재시도 등록). cancelStatus=" + cancelStatus;
        } catch (PortOneApiException cancelException) {  // 호출 자체가 실패
            enqueueCancelRetry(paymentId, cancelIdempotencyKey, reason, cancelFlow);

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
            enqueueCancelRetry(paymentId, cancelIdempotencyKey, reason, cancelFlow);

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
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("결제 시도 내역이 없습니다. paymentId=" + paymentId)
        );

        payment.refund();
        if (cancelFlow == CancelFlow.REFUND) {
            markExistingRefundRefunded(payment);
        } else {
            upsertRefundForCompensation(payment, reason);
        }

        // TODO: 주문 도메인 팀 구현 필요
        // - 재시도 큐를 통해 취소가 늦게 성공한 경우에도 주문 상태를 최종 취소 상태로 맞춰야 함
        // - 이미 취소된 주문이면 멱등 처리
        // orderService.cancelOrder(payment.getOrder().getId());

        // TODO: 상품/재고 도메인 팀 구현 필요
        // - 재시도 취소 성공 이후에도 주문 기준 재고 복구가 가능해야 함
        // - 중복 복구 방지를 위한 멱등 처리 필요
        // inventoryService.restoreStockByOrder(payment.getOrder().getId());

        // TODO: 포인트 도메인 팀 구현 필요
        // - 재시도 취소 성공 이후에도 사용 포인트 환원 / 적립 포인트 회수 처리가 가능해야 함
        // - 이미 처리된 환불 포인트 이력이 있으면 중복 처리하지 않도록 해야 함
        // pointTransactionService.refundUsedPoints(payment.getOrder().getOrderId());
    }

    // 보상트랜젝션에서 결제취소 성공
    private void upsertRefundForCompensation(Payment payment, String reason) {
        refundRepository.findByPayment(payment)
                .ifPresentOrElse(
                        Refund::markRefunded,
                        () -> refundRepository.save(Refund.createRefunded(payment, payment.getAmount(), reason))
                );
    }

    // 환불성공 상태변경
    private void markExistingRefundRefunded(Payment payment) {
        Refund refund = refundRepository.findByPayment(payment).orElseThrow(
                () -> new IllegalStateException("환불 요청 레코드가 없습니다. paymentId=" + payment.getPaymentId())
        );
        refund.markRefunded();
    }

    // 환불실패 상태변경
    private void markExistingRefundRetrying(Payment payment) {
        Refund refund = refundRepository.findByPayment(payment).orElseThrow(
                () -> new IllegalStateException("환불 요청 레코드가 없습니다. paymentId=" + payment.getPaymentId())
        );
        refund.markRetrying();
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
        refund.markFailed();
    }

    // 취소 재시도 큐등록
    private void enqueueCancelRetry(String paymentId, String idempotencyKey, String reason, CancelFlow cancelFlow) {
        boolean alreadyExists = paymentRetryTaskRepository.existsByPaymentIdAndOperationAndStatusIn(
                paymentId,
                PaymentRetryOperation.CANCEL_PAYMENT,
                Set.of(PaymentRetryStatus.PENDING, PaymentRetryStatus.PROCESSING)
        );
        if (alreadyExists) {
            return;
        }
        paymentRetryTaskRepository.save(PaymentRetryTask.cancelTask(paymentId, idempotencyKey, reason, cancelFlow));
    }
}
