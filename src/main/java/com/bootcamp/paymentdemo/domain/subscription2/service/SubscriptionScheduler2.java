package com.bootcamp.paymentdemo.domain.subscription2.service;

import com.bootcamp.paymentdemo.domain.subscription2.entity.Subscription2;
import com.bootcamp.paymentdemo.domain.subscription2.entity.SubscriptionStatus2;
import com.bootcamp.paymentdemo.domain.subscription2.repository.SubscriptionRepository2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionScheduler2 {

    private final SubscriptionRepository2 subscriptionRepository;
    private final SubscriptionService2 subscriptionService;

    /**
     * 매일 새벽 2시에 실행 (Cron 표현식: 초 분 시 일 월 요일)
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void runMonthlyBilling() {
        log.info("정기 결제 스케줄러 실행 시작: {}", LocalDateTime.now());

        // 1. 오늘 결제 대상자 조회
        List<Subscription2> targets = subscriptionRepository.findAllByStatusAndNextBillingDateBefore(
                SubscriptionStatus2.ACTIVE,
                LocalDateTime.now()
        );

        log.info("결제 대상자 수: {}명", targets.size());

        // 2. 한 명씩 결제 처리
        for (Subscription2 sub : targets) {
            try {
                // 이 메서드는 우리가 아까 만든 결제 요청 로직을 재사용하거나
                // 스케줄러용 전용 메서드를 호출합니다.
                subscriptionService.executeRecurringBilling(sub);
            } catch (Exception e) {
                log.error("구독 결제 중 오류 발생 - SubID: {}, 사유: {}", sub.getId(), e.getMessage());
            }
        }

        log.info("정기 결제 스케줄러 실행 종료");
    }
}
