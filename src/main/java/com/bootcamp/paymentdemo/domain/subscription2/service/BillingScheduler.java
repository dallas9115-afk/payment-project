package com.bootcamp.paymentdemo.domain.subscription2.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BillingScheduler {

    private final RecurringBillingService recurringBillingService;

    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "RecurringBillingLock", lockAtMostFor = "10m", lockAtLeastFor = "5m")
    public void runMonthlyBilling() {
        recurringBillingService.processAllRecurringBillings();
    }
}
