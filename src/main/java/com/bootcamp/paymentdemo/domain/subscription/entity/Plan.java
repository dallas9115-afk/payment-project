package com.bootcamp.paymentdemo.domain.subscription.entity;

import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


// 구독 플랜
@Entity
@Table(name = "plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Plan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // 이름
    @Column(nullable = false, unique = true)
    private String planName;


    // 가격
    @Column(nullable = false)
    private Integer price;


    // 구독 월,년 (예: 'monthly' or 'yearly')
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingInterval billingInterval;

    // 체험 기간.
    @Column(nullable = false)
    private Integer trialPeriodDays;

    // 구독 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status;

    private String description;

    public Plan(
            String planName,
            Integer price,
            BillingInterval billingInterval,
            Integer trialPeriodDays,
            String description
    ) {
        this.planName = planName;
        this.price = price;
        this.billingInterval = billingInterval;
        this.trialPeriodDays = trialPeriodDays == null ? 0 : trialPeriodDays;
        this.description = description;
        this.status = PlanStatus.ACTIVE;
    }

    // 체험 기간 확인 로직.
    public boolean hasTrial() {
        return trialPeriodDays > 0;
    }

    public void deactivate() {
        this.status = PlanStatus.INACTIVE;
    }

    public void activate() {
        this.status = PlanStatus.ACTIVE;
    }
}
