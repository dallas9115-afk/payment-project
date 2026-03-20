package com.bootcamp.paymentdemo.domain.payment.entity;

import com.bootcamp.paymentdemo.domain.payment.enums.PaymentRetryOperation;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentRetryStatus;
import com.bootcamp.paymentdemo.domain.refund.enums.CancelFlow;
import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "payment_retry_tasks",
        indexes = {
                @Index(name = "idx_retry_status_next", columnList = "status,next_attempt_at"),
                @Index(name = "idx_retry_payment_operation", columnList = "payment_id,operation")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE payment_retry_tasks SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class PaymentRetryTask extends BaseEntity {

    // 재시도용 일감 1개 객체

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentRetryOperation operation; // 뭘재시도할건지

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentRetryStatus status; // 재시도상태

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey; // 멱등키

    @Column(name = "cancel_reason")
    private String cancelReason; //취소사유

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_flow")
    private CancelFlow cancelFlow; // 보상취소인지 일반환불인지

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;  // 몇번재시도했는지

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;  // 얼마나 재시도할건지

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;  //다음재시도는 언제인지

    @Column(name = "last_error", length = 1000)
    private String lastError; // 마지막에러

    /**
     * 결제 확인 재시도 작업 생성
     * - PortOne 조회가 일시 실패했을 때 큐에 넣는 작업
     */
    public static PaymentRetryTask verifyTask(String paymentId, String idempotencyKey) {
        PaymentRetryTask task = new PaymentRetryTask();
        task.paymentId = paymentId;
        task.operation = PaymentRetryOperation.VERIFY_PAYMENT;
        task.status = PaymentRetryStatus.PENDING;
        task.idempotencyKey = idempotencyKey;
        task.attemptCount = 0;
        task.maxAttempts = 10;
        task.nextAttemptAt = LocalDateTime.now();
        return task;
    }

    /**
     * 결제 취소(보상 트랜잭션) 재시도 작업 생성
     * - 내부 처리 실패 후 PortOne 취소가 실패했을 때 큐에 넣는 작업
     */
    public static PaymentRetryTask cancelTask(
            String paymentId,
            String idempotencyKey,
            String cancelReason,
            CancelFlow cancelFlow
    ) {
        PaymentRetryTask task = new PaymentRetryTask();
        task.paymentId = paymentId;
        task.operation = PaymentRetryOperation.CANCEL_PAYMENT;
        task.status = PaymentRetryStatus.PENDING;
        task.idempotencyKey = idempotencyKey;
        task.cancelReason = cancelReason;
        task.cancelFlow = cancelFlow;
        task.attemptCount = 0;
        task.maxAttempts = 20;
        task.nextAttemptAt = LocalDateTime.now();
        return task;
    }

    // 스케줄러가 지금 처리 중인 작업으로 표시
    public void markProcessing() {
        this.status = PaymentRetryStatus.PROCESSING;
    }

    // 작업 성공 종료
    public void markSucceeded() {
        this.status = PaymentRetryStatus.SUCCEEDED;
        this.lastError = null;
    }

    /**
     * 재시도 예약
     * - 실패 시도 횟수를 올리고 다음 실행 시간을 미래로 미룹니다.
     * - maxAttempts를 넘으면 최종 FAILED로 종료합니다.
     */
    public void scheduleRetry(String errorMessage) {
        this.attemptCount++;
        this.lastError = errorMessage;
        this.status = PaymentRetryStatus.PENDING;

        if (this.attemptCount >= this.maxAttempts) {
            this.status = PaymentRetryStatus.FAILED;
            return;
        }

        // 단순 지수 백오프: 30s, 60s, 120s, ... (최대 30분 캡)
        long delaySeconds = Math.min(1800L, 30L * (1L << Math.min(this.attemptCount - 1, 5)));
        this.nextAttemptAt = LocalDateTime.now().plusSeconds(delaySeconds);
    }

    // 재시도 불가 오류 등으로 즉시 최종 실패 처리
    public void markFailedFinal(String errorMessage) {
        this.attemptCount++;
        this.lastError = errorMessage;
        this.status = PaymentRetryStatus.FAILED;
    }
}
