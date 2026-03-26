package com.bootcamp.paymentdemo.domain.subscription.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionScheduler {

    // [수정] 직접 서비스를 부르지 않고, 배치 전용 서비스를 주입받습니다.
    private final RecurringBillingService recurringBillingService;

    /**
     * 매일 새벽 2시에 실행
     * [피드백 반영] ShedLock을 추가하여 멀티 서버 중복 실행 방지
     */
    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "RecurringBillingJob", lockAtMostFor = "30m", lockAtLeastFor = "10m")
    public void runMonthlyBilling() {
        log.info("정기 결제 스케줄러 가동: {}", LocalDateTime.now());

        try {
            // [핵심] 이제 복잡한 루프나 페이징 로직은 이 서비스가 알아서 합니다.
            recurringBillingService.processAllRecurringBillings();

        } catch (Exception e) {
            log.error("정기 결제 배치 실행 중 치명적 오류 발생", e);
        }

        log.info("정기 결제 스케줄러 종료");
    }
}
