package com.bootcamp.paymentdemo.domain.subscription2.repository;

import com.bootcamp.paymentdemo.domain.subscription2.entity.BillingStatus2;
import com.bootcamp.paymentdemo.domain.subscription2.entity.Subscription2;
import com.bootcamp.paymentdemo.domain.subscription2.entity.SubscriptionBilling2;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionBillingRepository2 extends JpaRepository<SubscriptionBilling2, Long> {

    boolean existsBySubscriptionAndStatusInAndCreatedAtAfter(
            Subscription2 subscription,
            List<BillingStatus2> statuses,
            LocalDateTime after
    );

    boolean existsBySubscriptionAndScheduledDate(Subscription2 subscription, LocalDateTime scheduledDate);


    @Query("SELECT COUNT(b) > 0 FROM SubscriptionBilling2 b " +
            "WHERE b.subscription.customerId = :customerId " +
            "AND b.subscription.plan.id = :planId " +
            "AND b.createdAt >= :scheduledDate") // (주의: scheduledDate 필드가 없으면 createdAt 등으로 대체해야 함)
    boolean existsByCustomerAndPlanAndDate(
            @Param("customerId") Long customerId,
            @Param("planId") Long planId,
            @Param("scheduledDate") LocalDateTime scheduledDate);

    Optional<SubscriptionBilling2> findByPaymentId(String paymentId);
}
