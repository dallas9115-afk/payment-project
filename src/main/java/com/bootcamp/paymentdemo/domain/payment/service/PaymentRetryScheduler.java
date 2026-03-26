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

}
