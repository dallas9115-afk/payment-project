package com.bootcamp.paymentdemo.domain.subscription.entity;



import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_plan")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class SubscriptionPlan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Long price;

    @Enumerated(EnumType.STRING)
    private PlanStatus status; // ACTIVE인지 INACTIVE인지

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval")
    private BillingInterval billingInterval;

    private PlanLevel level;

    @Column(length = 500)
    private String description;

    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_interval")
    private BillingInterval interval;

    // 다음 결제일을 계산하는 로직 예시
    public LocalDateTime calculateNextBillingDate(LocalDateTime current) {
        if (this.billingInterval == BillingInterval.MONTHLY) {
            return current.plusMonths(1);
        }
        return current.plusYears(1);
    }


}
