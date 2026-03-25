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


// 구독 정보
@Entity
@Table(name = "plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Plan extends BaseEntity {

    private static final int MONTHLY_BASE_PRICE = 20000;
    private static final int YEARLY_BASE_PRICE = 200000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String planName;

    @Column(nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingInterval billingInterval;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanLevel planLevel;

    private String description;

    public Plan(
            String planName,
            PlanLevel planLevel,
            BillingInterval billingInterval,
            String description
    ) {
        this.planName = planName;
        this.planLevel = planLevel;
        this.billingInterval = billingInterval;
        this.description = description;
        this.price = calculatePrice();
    }

    public int calculatePrice() {
        int basePrice = billingInterval == BillingInterval.YEARLY ? YEARLY_BASE_PRICE : MONTHLY_BASE_PRICE;
        return basePrice + planLevel.getAdditionalAmount();
    }
}
