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
import org.h2.mvstore.type.LongDataType;

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

    @Column(nullable = false)
    private String paymentMethodId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(nullable = false)
    private LocalDateTime currentPeriodStart;

    @Column(nullable = false)
    private LocalDateTime currentPeriodEnd;

    private LocalDateTime canceledAt;
    private LocalDateTime endedAt;
    private LocalDateTime  banedAt;

    public Subscription(String subscriptionId, Customer customer, Plan plan, String paymentMethodId) {
        LocalDateTime now = LocalDateTime.now();

        this.subscriptionId = subscriptionId;
        this.customer = customer;
        this.plan = plan;
        this.paymentMethodId = paymentMethodId;
        this.currentPeriodStart = now;
        this.currentPeriodEnd = calculatePeriodEnd(now, plan.getBillingInterval());
        this.status = SubscriptionStatus.ACTIVE;
    }


    // 구독 계산 메서드
    private LocalDateTime calculatePeriodEnd(LocalDateTime startAt, BillingInterval billingInterval) {
        if (billingInterval == BillingInterval.YEARLY) {
            return startAt.plusYears(1);
        }
        return startAt.plusMonths(1);
    }

    public void activate() {
        this.status = SubscriptionStatus.ACTIVE;
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

    public void ban(){
        this.status=SubscriptionStatus.BAN;
        this.banedAt = LocalDateTime.now();
    }

    public void renew() {
        this.status = SubscriptionStatus.ACTIVE;
        this.currentPeriodStart = this.currentPeriodEnd;
        this.currentPeriodEnd = calculatePeriodEnd(this.currentPeriodEnd, this.plan.getBillingInterval());
    }
}
