package com.bootcamp.paymentdemo.domain.subscription2.repository;

import com.bootcamp.paymentdemo.domain.subscription2.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
}
