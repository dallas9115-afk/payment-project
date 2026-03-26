package com.bootcamp.paymentdemo.domain.subscription.repository;

import com.bootcamp.paymentdemo.domain.subscription.entity.Subscription;
import com.bootcamp.paymentdemo.domain.subscription.entity.SubscriptionBilling;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionBillingRepository extends JpaRepository<SubscriptionBilling, Long> {

    boolean existsBySubscriptionAndScheduledDate(Subscription subscription, LocalDateTime scheduledDate);


    @Query("SELECT COUNT(b) > 0 FROM SubscriptionBilling b " +
            "WHERE b.subscription.customer.id = :customerId " + // customerId -> customer.id
            "AND b.subscription.plan.id = :planId " +
            "AND b.createdAt >= :scheduledDate")
    boolean existsByCustomerAndPlanAndDate(
            @Param("customerId") Long customerId,
            @Param("planId") Long planId,
            @Param("scheduledDate") LocalDateTime scheduledDate);

    Optional<SubscriptionBilling> findByPaymentId(String paymentId);

    List<SubscriptionBilling> findAllBySubscriptionIdOrderByScheduledDateDesc(Long subscriptionId);

    Optional<SubscriptionBilling> findTopBySubscriptionIdOrderByIdDesc(Long subscriptionId);
}
