package com.bootcamp.paymentdemo.domain.subscription.entity;

import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


// 구독 테이블
@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String subscriptionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    // 구독 결제방법
    @Column(nullable = false)
    private String paymentMethodId;

    // 구독 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    // 구독 시작일
    @Column(nullable = false)
    private LocalDateTime currentPeriodStart;


    // 체험 구독 종료일
    @Column(nullable = false)
    private LocalDateTime currentPeriodEnd;

    private LocalDateTime trialEnd;
    private LocalDateTime canceledAt;
    private LocalDateTime endedAt;

    public Subscription(String subscriptionId, Customer customer, Plan plan, String paymentMethodId) {
        LocalDateTime now = LocalDateTime.now();

        this.subscriptionId = subscriptionId;
        this.customer = customer;
        this.plan = plan;
        this.paymentMethodId = paymentMethodId;
        this.currentPeriodStart = now;
        this.currentPeriodEnd = calculatePeriodEnd(now, plan.getBillingInterval());

        // 체험 구독 기간 검증 로직
        // trialPeriodDays > 0; 이라면  TRIALING 상태, 체험 구독 종료일 설정
        // 아니라면 체험 구독 기간 = 0: ACTIVE 상태, 즉시 결제
        if (plan.hasTrial()) {
            this.status = SubscriptionStatus.TRIALING;
            this.trialEnd = now.plusDays(plan.getTrialPeriodDays());
        } else {
            this.status = SubscriptionStatus.ACTIVE;
        }
    }

    private LocalDateTime calculatePeriodEnd(LocalDateTime startAt, BillingInterval billingInterval) {
        if (billingInterval == BillingInterval.YEARLY) {
            return startAt.plusYears(1);
        }
        return startAt.plusMonths(1);
    }

    // 구독 상태 변경 로직
    public void activate() {
        this.status = SubscriptionStatus.ACTIVE;
        this.trialEnd = null;
    }

    public void markPastDue() {
        this.status = SubscriptionStatus.PAST_DUE;
    }

    public void cancel() {
        this.status = SubscriptionStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    public void end() {
        this.status = SubscriptionStatus.ENDED;
        this.endedAt = LocalDateTime.now();
    }

    public void renew() {
        this.status = SubscriptionStatus.ACTIVE;
        this.currentPeriodStart = this.currentPeriodEnd;
        this.currentPeriodEnd = calculatePeriodEnd(this.currentPeriodEnd, this.plan.getBillingInterval());
    }
}
