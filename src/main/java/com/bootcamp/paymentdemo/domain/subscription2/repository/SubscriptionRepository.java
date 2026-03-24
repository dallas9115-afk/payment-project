package com.bootcamp.paymentdemo.domain.subscription2.repository;

import com.bootcamp.paymentdemo.domain.subscription2.entity.Subscription;
import com.bootcamp.paymentdemo.domain.subscription2.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {


    // 상태가 ACTIVE이고, 다음 결제일이 현재 시간보다 이전(오늘 포함)인 목록 조회
    List<Subscription> findAllByStatusAndNextBillingDateBefore(
            SubscriptionStatus status,
            LocalDateTime now
    );
}
