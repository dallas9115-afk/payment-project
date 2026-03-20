package com.bootcamp.paymentdemo.domain.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRetryScheduler {

    private final PaymentRetryService paymentRetryService;

    // 타이머 정해진시간마다 로직 실행하라고 명령하는곳

    /**
     * 결제 재시도 배치
     * - 일시 장애(네트워크/5xx/보상취소 실패)로 밀린 작업을 주기적으로 재실행합니다.
     * - fixedDelay: 이전 실행이 끝난 시점부터 지정 시간 후 다시 실행
     */
    @Scheduled(fixedDelayString = "${payment.retry.fixed-delay-ms:30000}") //30초뒤에 재실행해라
    public void processRetryTasks() {
        paymentRetryService.processPendingTasks();
    }

    @Scheduled(fixedDelayString = "${payment.expire.fixed-delay-ms:120000}")
    public void expirePayments() {
        paymentRetryService.expirePayments();
    }

    /**
     * 기존 팀원 스케줄러를 결제 도메인으로 흡수했습니다.
     * 지금은 정기결제 배치의 실행 자리만 유지하고, 실제 구독 조회/청구 로직은
     * 도메인 구현이 준비되면 이 메서드 안에 연결하면 됩니다.
     */
    @Scheduled(cron = "${payment.subscription.daily-cron:0 0 0 * * *}") //매일 00시00분에 실행
    public void processDailySubscriptionPayments() {
        log.info("[스케줄러 시작] 매일 자정 정기결제 배치가 실행되었습니다. time={}", LocalDateTime.now());

        try {
            // TODO: 구독 도메인 팀 구현 필요
            // - 책임: 매일 자정에 "오늘 결제해야 하는 활성 구독" 목록을 조회해서 정기결제를 실행
            // - 규칙:
            //   1) ACTIVE 상태이고 결제 예정일이 오늘 이하인 구독만 조회
            //   2) 구독별로 billingKey / 청구금액 / 다음 주기 정보를 확인
            //   3) PortOne 빌링키 결제를 호출하고 성공/실패 결과를 구독/청구 이력에 반영
            //   4) 이미 오늘 처리된 구독이면 중복 청구되지 않도록 멱등 처리
            //   5) 실패 건은 별도 재시도 정책 또는 미납 상태 정책이 필요
            // subscriptionService.processDueSubscriptions();
            log.info("[스케줄러 종료] 정기결제 배치가 무사히 완료되었습니다.");
        } catch (Exception e) {
            log.error("[스케줄러 에러] 정기결제 배치 실행 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}
