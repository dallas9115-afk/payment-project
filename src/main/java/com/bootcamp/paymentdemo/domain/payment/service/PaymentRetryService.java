package com.bootcamp.paymentdemo.domain.payment.service;

import com.bootcamp.paymentdemo.domain.payment.dto.Response.PortOnePaymentInfoResponse;
import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentRetryOperation;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentRetryStatus;
import com.bootcamp.paymentdemo.domain.payment.entity.PaymentRetryTask;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentStatus;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentRepository;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentRetryTaskRepository;
import com.bootcamp.paymentdemo.domain.refund.enums.CancelFlow;
import com.bootcamp.paymentdemo.global.error.PortOneApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // 재시도 큐에등록해주고, 재시도로직처리하는곳

    /**
     * 결제 검증 재시도 작업 등록
     * - 이미 같은 작업이 큐에 있으면 중복 등록하지 않습니다.
     */
    @Transactional
    public void enqueueVerifyRetry(String paymentId, String idempotencyKey) {
        boolean alreadyExists = paymentRetryTaskRepository.existsByPaymentIdAndOperationAndStatusIn(
                paymentId,
                PaymentRetryOperation.VERIFY_PAYMENT,
                Set.of(PaymentRetryStatus.PENDING, PaymentRetryStatus.PROCESSING)
        );
        if (alreadyExists) {
            return;
        }
        paymentRetryTaskRepository.save(PaymentRetryTask.verifyTask(paymentId, idempotencyKey));
        log.warn("VERIFY 재시도 작업 등록 - paymentId={}, key={}", paymentId, idempotencyKey);
    }

    /**
     * 결제 취소 재시도 작업 등록
     * - 보상 취소 실패 시 호출됩니다.
     */
    @Transactional
    public void enqueueCancelRetry(String paymentId, String idempotencyKey, String reason) {
        enqueueCancelRetry(paymentId, idempotencyKey, reason, CancelFlow.COMPENSATION);
    }

    @Transactional
    public void enqueueCancelRetry(String paymentId, String idempotencyKey, String reason, CancelFlow cancelFlow) {
        boolean alreadyExists = paymentRetryTaskRepository.existsByPaymentIdAndOperationAndStatusIn(
                paymentId,
                PaymentRetryOperation.CANCEL_PAYMENT,
                Set.of(PaymentRetryStatus.PENDING, PaymentRetryStatus.PROCESSING)
        );
        if (alreadyExists) {
            return;
        }
        paymentRetryTaskRepository.save(PaymentRetryTask.cancelTask(paymentId, idempotencyKey, reason, cancelFlow));
        log.error("CANCEL 재시도 작업 등록 - paymentId={}, key={}, reason={}, flow={}",
                paymentId, idempotencyKey, reason, cancelFlow);
    }

    /**
     * 스케줄러가 주기적으로 호출하는 메서드
     * - 지금 시각 기준 실행 가능한 PENDING 작업을 최대 100건 가져와 처리합니다.
     */
    @Transactional
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
     * - PortOne 오류는 retryable 플래그에 따라 재시도/최종실패를 결정
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
                handleRetryTaskFinalFailure(task);
            } else {
                task.markFailedFinal(e.getMessage());
                handleRetryTaskFinalFailure(task);
            }
            log.error("재시도 작업 처리 실패(포트원) - taskId={}, operation={}, paymentId={}, retryable={}, error={}",
                    task.getId(), task.getOperation(), task.getPaymentId(), e.isRetryable(), e.getMessage(), e);
        } catch (Exception e) {
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

        PortOnePaymentInfoResponse info = portOneApiClient.getPaymentInfo(task.getPaymentId(), task.getIdempotencyKey());

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

        PortOnePaymentInfoResponse cancelResult = portOneApiClient.paymentCancel(
                task.getPaymentId(),
                task.getCancelReason(),
                task.getIdempotencyKey()
        );

        String status = cancelResult.getStatus();
        if ("CANCELLED".equalsIgnoreCase(status) || "PARTIAL_CANCELLED".equalsIgnoreCase(status)) {
            paymentLifecycleService.markRefundedAfterCancel(
                    task.getPaymentId(),
                    task.getCancelReason(),
                    task.getCancelFlow()
            );
            task.markSucceeded();
            log.info("CANCEL 재시도 성공 - paymentId={}, status={}", task.getPaymentId(), status);
            return;
        }

        task.scheduleRetry("취소 상태 미확정 status=" + status);
        handleRetryTaskFinalFailure(task);
    }

    public void expirePayments() {
        // payment 상태가 ready 인것들중에
        // expiresAt <= now 인것들을 찾음
        // 그리고 하나씩 task가 있는지 없는지 검사
        // task가 없다면 status = EXPIRED 로 변환
        // EXPIRED 로 변환했다면 주문상태를 변경할지말지 정책을정해야함
        List<Payment> payments = paymentRepository.findByStatusAndExpiresAtLessThanEqual(PaymentStatus.READY, LocalDateTime.now());
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
                // TODO: 주문에서 결제대기 -> 주문취소로 바꾸는 로직 들어갈곳
                payment.expire();
            }
        }
    }

    private void handleRetryTaskFinalFailure(PaymentRetryTask task) {
        if (task.getStatus() != PaymentRetryStatus.FAILED) { // 테스크 재시도 실패인지확인
            return;
        }

        if (task.getOperation() == PaymentRetryOperation.VERIFY_PAYMENT) { // 결제 재시도 실패확정 결제상태변경
            paymentLifecycleService.markFailed(task.getPaymentId());
            return;
        }

        if (task.getOperation() == PaymentRetryOperation.CANCEL_PAYMENT  // 환불 재시도 실패확정 환불상태변경
                && task.getCancelFlow() == CancelFlow.REFUND) {
            paymentLifecycleService.markRefundFailed(task.getPaymentId());
            return;
        }

        if (task.getOperation() == PaymentRetryOperation.CANCEL_PAYMENT // 보상트랜젝션 재시도 실패확정 결제상태변경
                && task.getCancelFlow() == CancelFlow.COMPENSATION) {
            paymentLifecycleService.markFailed(task.getPaymentId());
        }
    }
}
