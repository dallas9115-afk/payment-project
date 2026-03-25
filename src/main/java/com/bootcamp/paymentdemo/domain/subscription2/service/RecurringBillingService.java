package com.bootcamp.paymentdemo.domain.subscription2.service;

import com.bootcamp.paymentdemo.domain.subscription2.entity.Subscription2;
import com.bootcamp.paymentdemo.domain.subscription2.repository.SubscriptionRepository2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;


@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringBillingService {

    private final SubscriptionRepository2 subscriptionRepository;
    private final SubscriptionService2 subscriptionService;

    public void processAllRecurringBillings() {
        LocalDateTime now = LocalDateTime.now();
        int pageSize = 100;
        Long lastId = 0L;

        // [피드백 1] 스레드 개수와 큐 크기 제한
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                10, 10, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1000),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        log.info("정기 결제 배치 엔진 가동 - 기준 시간: {}", now);

        try {
            while (true) {
                // [피드백 4] ID 정렬 기반 커서 조회 (누락 방지)
                Pageable pageable = PageRequest.of(0, pageSize, Sort.by("id").ascending());
                List<Subscription2> targets = subscriptionRepository.findBillingTargetsCursor(now, lastId, pageable);

                if (targets.isEmpty()) break;

                Long maxIdInBatch = lastId;

                for (Subscription2 sub : targets) {
                    // [피드백 2] 과부하 방지 (Back-pressure): 큐가 너무 차면 잠시 대기
                    while (executor.getQueue().size() > 800) {
                        log.debug("스레드 풀 큐가 가득 참. 대기 중...");
                        Thread.sleep(100);
                    }

                    String paymentId = "SUB_RECUR_" + sub.getId() + "_" + UUID.randomUUID().toString().substring(0, 8);

                    // [피드백 3] 엔티티 대신 ID 전달
                    Long targetId = sub.getId();
                    executor.submit(() -> subscriptionService.executeRecurringBilling(targetId, paymentId));

                    maxIdInBatch = targetId;
                }

                lastId = maxIdInBatch;

                if (targets.size() < pageSize) break;
            }
        } catch (InterruptedException e) {
            log.error("정기 결제 배치 중 인터럽트 발생", e);
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
    }
}
