package com.bootcamp.paymentdemo.domain.subscription2.repository;

import com.bootcamp.paymentdemo.domain.subscription2.entity.PaymentMethod2;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionPaymentMethodRepository extends JpaRepository<PaymentMethod2, Long> {
    Optional<PaymentMethod2> findByCustomerIdAndIsDefaultTrue(Long customerId);
}
