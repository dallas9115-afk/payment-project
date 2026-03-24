package com.bootcamp.paymentdemo.domain.payment.service;

import com.bootcamp.paymentdemo.domain.payment.entity.PaymentRetryTask;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentRetryOperation;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentRetryStatus;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentRetryTaskRepository;
import com.bootcamp.paymentdemo.domain.refund.dto.RefundCalculation;
import com.bootcamp.paymentdemo.domain.refund.enums.CancelFlow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRetryTaskService {

    private final PaymentRetryTaskRepository paymentRetryTaskRepository;

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
     * - 환불이면 부분취소 금액/회수 포인트를 같이 저장해 다음 재시도에서도 같은 값을 사용합니다.
     * - 보상취소면 계산값 없이 전액 취소 기준으로 등록합니다.
     */
    @Transactional
    public void enqueueCancelRetry(
            String paymentId,
            String idempotencyKey,
            String reason,
            CancelFlow cancelFlow,
            RefundCalculation refundCalculation
    ) {
        boolean alreadyExists = paymentRetryTaskRepository.existsByPaymentIdAndOperationAndStatusIn(
                paymentId,
                PaymentRetryOperation.CANCEL_PAYMENT,
                Set.of(PaymentRetryStatus.PENDING, PaymentRetryStatus.PROCESSING)
        );
        if (alreadyExists) {
            return;
        }

        paymentRetryTaskRepository.save(
                PaymentRetryTask.cancelTask(
                        paymentId,
                        idempotencyKey,
                        reason,
                        cancelFlow,
                        refundCalculation == null ? null : refundCalculation.cancelAmount(),
                        refundCalculation == null ? null : refundCalculation.recoverableEarnedPoints()
                )
        );
        log.error("CANCEL 재시도 작업 등록 - paymentId={}, key={}, reason={}, flow={}",
                paymentId, idempotencyKey, reason, cancelFlow);
    }
}
