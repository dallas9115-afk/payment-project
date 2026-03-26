package com.bootcamp.paymentdemo.domain.subscription.repository;

import com.bootcamp.paymentdemo.domain.subscription.entity.SubscriptionPaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionPaymentMethodRepository extends JpaRepository<SubscriptionPaymentMethod, Long> {
    Optional<SubscriptionPaymentMethod> findByCustomerIdAndIsDefaultTrue(Long customerId);
}
