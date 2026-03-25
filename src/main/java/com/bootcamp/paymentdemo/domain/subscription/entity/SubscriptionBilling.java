package com.bootcamp.paymentdemo.domain.subscription.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_billing")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionBilling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    private Long amount;
    private String paymentId;


    @Enumerated(EnumType.STRING)
    private BillingStatus billingStatus;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt; // 생성일


    private LocalDateTime billedAt; // 결제 시도일

    private LocalDateTime billingPeriodStartAt; // 청구 기간

    private LocalDateTime billingPeriodEndAt; // 청구 기간 종료일


    @Column(length = 50)
    private String errorMessage;



    public SubscriptionBilling(
            Subscription subscription,
            Long amount,
            String paymentId,
            BillingStatus billingStatus,
            LocalDateTime billedAt,
            LocalDateTime billingPeriodStartAt,
            LocalDateTime billingPeriodEndAt,
            String errorMessage
    ) {
        this.subscription = subscription;
        this.amount = amount;
        this.paymentId = paymentId;
        this.billingStatus = billingStatus;
        this.billedAt = billedAt;
        this.billingPeriodStartAt = billingPeriodStartAt;
        this.billingPeriodEndAt = billingPeriodEndAt;
        this.errorMessage = errorMessage;
    }


    // static이라 호출가능. 성공 했을때.
    public static SubscriptionBilling success(
            Subscription subscription,
            Long amount,
            LocalDateTime billedAt,
            LocalDateTime billingPeriodStartAt,
            LocalDateTime billingPeriodEndAt
    ) {
        return new SubscriptionBilling(
                subscription,
                amount,
                null,
                BillingStatus.SUCCESS,
                billedAt,
                billingPeriodStartAt,
                billingPeriodEndAt,
                null
        );
    }


    // static이라 호출가능. 실패 했을때.
    public static SubscriptionBilling fail(
            Subscription subscription,
            Long amount,
            LocalDateTime billedAt,
            LocalDateTime billingPeriodStartAt,
            LocalDateTime billingPeriodEndAt,
            String errorMessage
    ) {
        return new SubscriptionBilling(
                subscription,
                amount,
                null,
                BillingStatus.FAILED,
                billedAt,
                billingPeriodStartAt,
                billingPeriodEndAt,
                errorMessage
        );
    }



}
