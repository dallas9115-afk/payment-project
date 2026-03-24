package com.bootcamp.paymentdemo.domain.subscription2.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "subscription_billings")
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
}
