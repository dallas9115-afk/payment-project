package com.bootcamp.paymentdemo.domain.subscription2.repository;

import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.subscription2.entity.BillingStatus;
import com.bootcamp.paymentdemo.domain.subscription2.entity.Subscription;
import com.bootcamp.paymentdemo.domain.subscription2.entity.SubscriptionBilling;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionBillingRepository extends JpaRepository<SubscriptionBilling, Long> {
    boolean existsBySubscriptionAndStatusInAndCreatedAtAfter(
            Subscription subscription,
            List<BillingStatus> statuses,
            LocalDateTime after
    );

    boolean existsBySubscriptionAndScheduledDate(Subscription subscription, LocalDateTime scheduledDate);

    boolean existsByCustomerIdAndPlanIdAndScheduledDate(Long customerId, Long planId, LocalDateTime today);

    Optional<SubscriptionBilling> findByPaymentId(String paymentId);
}
