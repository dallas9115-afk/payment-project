package com.bootcamp.paymentdemo.domain.subscription.repository;

import com.bootcamp.paymentdemo.domain.subscription.entity.Subscription;
import com.bootcamp.paymentdemo.domain.subscription.entity.SubscriptionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {


    // 상태가 ACTIVE이고, 다음 결제일이 현재 시간보다 이전(오늘 포함)인 목록 조회
    List<Subscription> findAllByStatusAndNextBillingDateBefore(
            SubscriptionStatus status,
            LocalDateTime now
    );

    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND s.nextBillingDate <= :now")
    Slice<Subscription> findAllBillingTargets(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT s FROM Subscription s " +
            "WHERE s.status = 'ACTIVE' " +
            "AND s.nextBillingDate <= :now " +
            "AND s.id > :lastId " + // [피드백 1] 핵심: 커서 기반 조회
            "ORDER BY s.id ASC")
    List<Subscription> findBillingTargetsCursor(
            @Param("now") LocalDateTime now,
            @Param("lastId") Long lastId,
            Pageable pageable);
}
