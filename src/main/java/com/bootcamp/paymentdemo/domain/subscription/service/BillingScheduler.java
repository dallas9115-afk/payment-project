package com.bootcamp.paymentdemo.domain.subscription.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BillingScheduler {

    private final RecurringBillingService recurringBillingService;

    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시
    @SchedulerLock(name = "RecurringBillingJob", lockAtMostFor = "20m", lockAtLeastFor = "10m")
    public void runMonthlyBilling() {
        recurringBillingService.processAllRecurringBillings();
    }
}
