package com.bootcamp.paymentdemo.domain.subscription2.entity;


import com.bootcamp.paymentdemo.domain.subscription.entity.BillingInterval;
import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "subscription_plan2")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionPlan2 extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Long price;

    @Enumerated(EnumType.STRING)
    private BillingInterval billingInterval;

    private int trialPeriodDays;


}
