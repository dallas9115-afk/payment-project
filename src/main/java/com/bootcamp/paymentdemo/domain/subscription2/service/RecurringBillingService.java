package com.bootcamp.paymentdemo.domain.subscription2.service;

import com.bootcamp.paymentdemo.domain.subscription2.entity.Subscription2;
import com.bootcamp.paymentdemo.domain.subscription2.repository.SubscriptionRepository2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringBillingService {

    private final SubscriptionRepository2 subscriptionRepository;
    private final SubscriptionService2 subscriptionService;

    public void processAllRecurringBillings() {
        LocalDateTime now = LocalDateTime.now();
        int pageSize = 100;
        int pageNumber = 0;

        // [피드백 반영 1] 동시 실행 개수를 10개로 제한 (Rate Limit 방어)
        ExecutorService executor = Executors.newFixedThreadPool(10);

        log.info("정기 결제 프로세스 시작 - 기준 시간: {}", now);

        try {
            while (true) {
                // [피드백 반영 4] 정렬(Sort.by("id")) 추가로 데이터 누락/중복 방지
                Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by("id").ascending());

                Slice<Subscription2> targetSlice = subscriptionRepository.findAllBillingTargets(now, pageable);
                List<Subscription2> targets = targetSlice.getContent();

                if (targets.isEmpty()) break;

                // [피드백 반영 1] ExecutorService를 이용한 스레드 제어
                for (Subscription2 sub : targets) {
                    executor.submit(() -> {
                        try {
                            log.info("정기 결제 시도: SubID={}, PaymentID 예정", sub.getId());
                            subscriptionService.executeRecurringBilling(sub);
                        } catch (Exception e) {
                            log.error("정기 결제 작업 중 예외 발생 - SubID: {}", sub.getId(), e);
                        }
                    });
                }

                if (!targetSlice.hasNext()) break;
                pageNumber++;
            }
        } finally {
            // [피드백 반영 2] 모든 작업이 끝날 때까지 대기 (Graceful Shutdown)
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("정기 결제 모든 프로세스 종료");
    }
}
