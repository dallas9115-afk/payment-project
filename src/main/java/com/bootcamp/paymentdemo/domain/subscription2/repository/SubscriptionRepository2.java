package com.bootcamp.paymentdemo.domain.subscription2.repository;

import com.bootcamp.paymentdemo.domain.subscription2.entity.Subscription2;
import com.bootcamp.paymentdemo.domain.subscription2.entity.SubscriptionStatus2;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SubscriptionRepository2 extends JpaRepository<Subscription2, Long> {


    // 상태가 ACTIVE이고, 다음 결제일이 현재 시간보다 이전(오늘 포함)인 목록 조회
    List<Subscription2> findAllByStatusAndNextBillingDateBefore(
            SubscriptionStatus2 status,
            LocalDateTime now
    );

    @Query("SELECT s FROM Subscription2 s WHERE s.status = 'ACTIVE' AND s.nextBillingDate <= :now")
    Slice<Subscription2> findAllBillingTargets(@Param("now") LocalDateTime now, Pageable pageable);
}
