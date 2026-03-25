package com.bootcamp.paymentdemo.domain.subscription.repository;


import com.bootcamp.paymentdemo.domain.subscription.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findBySubscriptionId(String subscriptionId);
}
