package com.bootcamp.paymentdemo.domain.subscription2.entity;


import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions2", indexes = {
        @Index(name = "idx_subscription_next_billing", columnList = "nextBillingDate, status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Subscription2 extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private SubscriptionPlan2 plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod2 paymentMethod;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus2 status; // PENDING(빌링키 발급 이후 1차저장), ACTIVE(결제 완료 후 2차저장), PAST_DUE(미납), CANCELED(해지)

    private LocalDateTime nextBillingDate;  // 다음 결제일 <- 스케줄러 활용
    private LocalDateTime trialEndDate;  // 체험 종료일

    public void updateStatus(SubscriptionStatus2 subscriptionStatus) {
        this.status = subscriptionStatus;
    }

    public void activate() {
        this.status = SubscriptionStatus2.ACTIVE; // 상태를 ACTIVE로 변경

        // 다음 결제일 갱신 (현재 시간 혹은 기존 결제일 기준 + 1개월)
        // 보통 결제가 완료된 시점으로부터 한 달 뒤로 설정합니다.
        if (this.nextBillingDate == null) {
            this.nextBillingDate = LocalDateTime.now().plusMonths(1);
        } else {
            // 이미 날짜가 있다면 그 날짜로부터 한 달 더하기 (정기 결제 시)
            this.nextBillingDate = this.nextBillingDate.plusMonths(1);
        }
    }

    public void cancel() {
        this.status = SubscriptionStatus2.CANCELED;
        // 다음 결제일을 null로 만들어서 스케줄러 대상에서 제외시킵니다.
        this.nextBillingDate = null;
    }

    public void startTrial(int trialDays) {
        this.status = SubscriptionStatus2.TRIALING; // 상태 변경
        this.trialEndDate = LocalDateTime.now().plusDays(trialDays); // 체험 종료일 설정

        // 체험 기간이 끝나고 나서 결제가 일어나야 하므로
        // 다음 결제 예정일(nextBillingDate)도 체험 종료일로 맞춰둡니다.
        this.nextBillingDate = this.trialEndDate;
    }

    public void toPastDue() {
        this.status = SubscriptionStatus2.PAST_DUE;
        // 여기서 알림 발송 이벤트를 던지거나 로그를 남깁니다.
    }

    /**
     * 다음 결제 예정일을 강제로 설정하거나 갱신할 때 사용합니다.
     */
    public void setNextBillingDate(LocalDateTime nextBillingDate) {
        this.nextBillingDate = nextBillingDate;
    }

    /**
     * [추가 권장] 현재 날짜 기준으로 한 달을 더해 결제일을 갱신하는 비즈니스 로직
     * (confirmSubscription 이나 스케줄러 성공 시 사용하면 편리합니다)
     */
    public void renewNextBillingDate() {
        if (this.nextBillingDate == null) {
            this.nextBillingDate = LocalDateTime.now().plusMonths(1);
        } else {
            this.nextBillingDate = this.nextBillingDate.plusMonths(1);
        }
    }
}
