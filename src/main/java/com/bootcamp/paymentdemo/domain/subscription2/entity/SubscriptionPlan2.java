package com.bootcamp.paymentdemo.domain.subscription2.entity;



import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_plan2")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class SubscriptionPlan2 extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Long price;

    @Enumerated(EnumType.STRING)
    private PlanStatus2 status; // ACTIVE인지 INACTIVE인지

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval2")
    private BillingInterval2 billingInterval;

    private int trialPeriodDays;

    private PlanLevel2 level;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_interval")
    private BillingInterval2 interval;

    // 다음 결제일을 계산하는 로직 예시
    public LocalDateTime calculateNextBillingDate(LocalDateTime current) {
        if (this.billingInterval == BillingInterval2.MONTHLY) {
            return current.plusMonths(1);
        }
        return current.plusYears(1);
    }


}
