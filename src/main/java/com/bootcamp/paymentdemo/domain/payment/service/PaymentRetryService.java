package com.bootcamp.paymentdemo.domain.payment.service;

import com.bootcamp.paymentdemo.domain.order.service.OrderService;
import com.bootcamp.paymentdemo.domain.payment.dto.Response.PortOnePaymentInfoResponse;
import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentRetryOperation;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentRetryStatus;
import com.bootcamp.paymentdemo.domain.payment.entity.PaymentRetryTask;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentStatus;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentRepository;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentRetryTaskRepository;
import com.bootcamp.paymentdemo.domain.refund.dto.RefundCalculation;
import com.bootcamp.paymentdemo.domain.refund.enums.CancelFlow;
import com.bootcamp.paymentdemo.global.error.PortOneApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRetryService {

    private final PaymentRetryTaskRepository paymentRetryTaskRepository;
    private final PaymentRepository paymentRepository;
    private final PortOneApiClient portOneApiClient;
    private final PaymentLifecycleService paymentLifecycleService;
    private final OrderService orderService;

    /**
     * 스케줄러가 주기적으로 호출하는 재시도 실행 진입점
     * - 지금 시각 기준 실행 가능한 PENDING 작업을 최대 100건 가져와 처리합니다.
     */
    public void processPendingTasks() {
        List<PaymentRetryTask> tasks =
                paymentRetryTaskRepository.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                        PaymentRetryStatus.PENDING,
                        LocalDateTime.now()
                );

        for (PaymentRetryTask task : tasks) {
            processTask(task);
        }
    }

    /**
     * 개별 작업 처리 공통 래퍼
     * - PortOneApiException은 retryable 플래그에 따라 재시도/최종실패를 결정합니다.
     * - 기타 예외는 일시적인 내부 오류 가능성을 보고 일단 재시도 대상으로 둡니다.
     */
    private void processTask(PaymentRetryTask task) {
        try {
            task.markProcessing();
            switch (task.getOperation()) {
                case VERIFY_PAYMENT -> processVerify(task);
                case CANCEL_PAYMENT -> processCancel(task);
            }
        } catch (PortOneApiException e) {
            if (e.isRetryable()) {
                task.scheduleRetry(e.getMessage());
                handleRetryTaskFinalFailure(task); // 최대 재시도 횟수를 넘겨 FAILED로 바뀐 경우만 후속 실패 처리
            } else {
                task.markFailedFinal(e.getMessage());
                handleRetryTaskFinalFailure(task);
            }
            log.error("재시도 작업 처리 실패(포트원) - taskId={}, operation={}, paymentId={}, retryable={}, error={}",
                    task.getId(), task.getOperation(), task.getPaymentId(), e.isRetryable(), e.getMessage(), e);
        } catch (Exception e) { // 예상하지 못한 예외도 우선 재시도 대상으로 둔다.
            task.scheduleRetry(e.getMessage());
            handleRetryTaskFinalFailure(task);
            log.error("재시도 작업 처리 실패 - taskId={}, operation={}, paymentId={}, error={}",
                    task.getId(), task.getOperation(), task.getPaymentId(), e.getMessage(), e);
        }
    }

    /**
     * VERIFY 작업 처리
     * - 같은 paymentId 결제건을 다시 조회하고, 조건이 맞으면 confirm 처리
     */
    private void processVerify(PaymentRetryTask task) {
        Payment payment = paymentLifecycleService.getPayment(task.getPaymentId());

        if (payment.isAlreadyProcessed()) {
            task.markSucceeded();
            return;
        }

        PortOnePaymentInfoResponse info =
                portOneApiClient.getPaymentInfo(task.getPaymentId(), task.getIdempotencyKey());

        // PAID가 아니면 최종 실패 상태인지, 아직 미확정 상태인지 구분해 처리한다.
        if (!info.isPaidStatus()) {
            if ("FAILED".equalsIgnoreCase(info.getStatus()) || "CANCELLED".equalsIgnoreCase(info.getStatus())) {
                paymentLifecycleService.markFailed(task.getPaymentId());
                task.markFailedFinal("결제 최종 실패 status=" + info.getStatus());
                return;
            }
            task.scheduleRetry("결제 상태 미완료 status=" + info.getStatus());
            handleRetryTaskFinalFailure(task);
            return;
        }

        try {
            paymentLifecycleService.completeApprovedPayment(task.getPaymentId(), info);
            task.markSucceeded();
            log.info("VERIFY 재시도 성공 - paymentId={}", task.getPaymentId());
        } catch (Exception processingException) {
            String compensationMessage = paymentLifecycleService.compensateApprovedPayment(
                    task.getPaymentId(),
                    "VERIFY 재시도 중 내부 처리 실패로 취소"
            );
            task.markFailedFinal("내부 처리 실패: " + processingException.getMessage() + " | " + compensationMessage);
        }
    }

    /**
     * CANCEL 작업 처리
     * - 이미 REFUNDED면 성공으로 종료
     * - 취소 응답이 CANCELLED 계열이면 환불 상태로 반영
     */
    private void processCancel(PaymentRetryTask task) {
        Payment payment = paymentLifecycleService.getPayment(task.getPaymentId());

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            task.markSucceeded();
            return;
        }

        // 환불 재시도면 처음 계산해둔 부분취소 금액을 그대로 재사용한다.
        PortOnePaymentInfoResponse cancelResult = portOneApiClient.paymentCancel(
                task.getPaymentId(),
                task.getCancelReason(),
                task.getIdempotencyKey(),
                task.getCancelAmount()
        );

        String status = cancelResult.getStatus();
        if ("CANCELLED".equalsIgnoreCase(status) || "PARTIAL_CANCELLED".equalsIgnoreCase(status)) {
            paymentLifecycleService.markRefundedAfterCancel(
                    task.getPaymentId(),
                    task.getCancelReason(),
                    task.getCancelFlow(),
                    buildRefundCalculation(task)
            );
            task.markSucceeded();
            log.info("CANCEL 재시도 성공 - paymentId={}, status={}", task.getPaymentId(), status);
            return;
        }

        task.scheduleRetry("취소 상태 미확정 status=" + status);
        handleRetryTaskFinalFailure(task);
    }

    /**
     * READY 상태인데 만료 시간이 지난 결제를 만료 처리합니다.
     * - VERIFY 재시도 작업이 살아있는 결제는 아직 확인 중인 건으로 보고 건드리지 않습니다.
     * - 재시도 작업이 없는 결제만 EXPIRED + 주문 취소 처리합니다.
     */
    public void expirePayments() {
        List<Payment> payments = paymentRepository.findByStatusAndExpiresAtLessThanEqual(
                PaymentStatus.READY,
                LocalDateTime.now()
        );
        List<String> paymentIds = payments.stream()
                .map(Payment::getPaymentId)
                .toList();
        Set<String> paymentIdsWithTask =
                paymentRetryTaskRepository.findPaymentIdsByPaymentIdInAndOperationAndStatusIn(
                        paymentIds,
                        PaymentRetryOperation.VERIFY_PAYMENT,
                        Set.of(PaymentRetryStatus.PENDING, PaymentRetryStatus.PROCESSING)
                );
        for (Payment payment : payments) {
            if (!paymentIdsWithTask.contains(payment.getPaymentId())) {
                orderService.cancelOrder(payment.getOrder().getId());
                payment.expire();
            }
        }
    }

    // 재시도 작업이 최종 FAILED로 끝난 경우에만 결제/환불 상태를 함께 정리한다.
    private void handleRetryTaskFinalFailure(PaymentRetryTask task) {
        if (task.getStatus() != PaymentRetryStatus.FAILED) {
            return;
        }

        if (task.getOperation() == PaymentRetryOperation.VERIFY_PAYMENT) {
            paymentLifecycleService.markFailed(task.getPaymentId());
            return;
        }

        if (task.getOperation() == PaymentRetryOperation.CANCEL_PAYMENT
                && task.getCancelFlow() == CancelFlow.REFUND) {
            paymentLifecycleService.markRefundFailed(task.getPaymentId());
            return;
        }

        if (task.getOperation() == PaymentRetryOperation.CANCEL_PAYMENT
                && task.getCancelFlow() == CancelFlow.COMPENSATION) {
            paymentLifecycleService.markFailed(task.getPaymentId());
        }
    }

    // 환불 재시도는 처음 계산한 취소 금액/포인트 회수량을 태스크에서 다시 복원해 사용한다.
    private RefundCalculation buildRefundCalculation(PaymentRetryTask task) {
        if (task.getCancelFlow() != CancelFlow.REFUND) {
            return null;
        }

        Long cancelAmount = task.getCancelAmount();
        Long recoverableEarnedPoints = task.getRecoverableEarnedPoints();
        Long unrecoverableEarnedPoints = null;

        if (cancelAmount != null && recoverableEarnedPoints != null) {
            Payment payment = paymentLifecycleService.getPayment(task.getPaymentId());
            unrecoverableEarnedPoints = payment.getPgAmount() - cancelAmount;
        }

        return new RefundCalculation(cancelAmount, recoverableEarnedPoints, unrecoverableEarnedPoints);
    }
}
