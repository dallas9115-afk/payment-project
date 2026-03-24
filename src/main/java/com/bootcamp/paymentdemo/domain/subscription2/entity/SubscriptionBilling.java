package com.bootcamp.paymentdemo.domain.subscription2.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "subscription_billing",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "unique_subscription_scheduled_date",
                        columnNames = {"subscription_id", "scheduled_date"} // 이 두 조합은 유일해야 함
                )
        }
)

// 쿼리 콘솔에 CREATE UNIQUE INDEX idx_sub_billing_unique
//ON subscription_billing (subscription_id, scheduled_date); 반드시 입력해주세요
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class SubscriptionBilling {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    private Long amount;
    private String paymentId;

    private LocalDateTime scheduledDate;

    @Enumerated(EnumType.STRING)
    private BillingStatus status; // READY(시도전), SUCCESS(성공), FAILED(실패)

    private String errorMessage; // 실패 사유


    public void updateStatus(BillingStatus billingStatus) {
        this.status = billingStatus;
    }

    public void complete(String paymentId) {
        this.status = BillingStatus.SUCCESS; // 상태 변경
        this.paymentId = paymentId;          // 포트원 거래 고유 번호 저장
        // 로깅이나 감사용으로 결제 완료 시간 등을 추가로 기록해도 좋습니다.
    }

    public void fail(String message) {
        this.status = BillingStatus.FAILED;
        this.errorMessage = message; // 실패 사유를 DB에 남겨야 나중에 CS 대응이 가능합니다!
    }

    public void markRequested(String paymentId) {
        this.status = BillingStatus.REQUESTED;
        this.paymentId = paymentId; // 👈 나중에 웹훅(confirm)에서 이 ID로 찾아야 함!
    }

    public boolean isCompleted() {
        return this.status == BillingStatus.SUCCESS ||
                this.status == BillingStatus.FAILED;
    }
}
