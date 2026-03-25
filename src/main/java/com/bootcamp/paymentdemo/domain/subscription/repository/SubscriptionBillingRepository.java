package com.bootcamp.paymentdemo.domain.subscription.repository;

import com.bootcamp.paymentdemo.domain.subscription.entity.SubscriptionBilling;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionBillingRepository extends JpaRepository<SubscriptionBilling, Long> {
}
