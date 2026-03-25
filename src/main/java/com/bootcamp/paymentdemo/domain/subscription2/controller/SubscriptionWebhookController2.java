package com.bootcamp.paymentdemo.domain.subscription2.controller;

import com.bootcamp.paymentdemo.domain.payment.dto.Request.PortOneWebhookRequest;
import com.bootcamp.paymentdemo.domain.subscription2.service.SubscriptionService2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class SubscriptionWebhookController2 {

    private final SubscriptionService2 subscriptionService;

    @PostMapping("/portone")
    public ResponseEntity<Void> handlePortOneWebhook(@RequestBody PortOneWebhookRequest request) {
        log.info("포트원 V2 웹훅 수신: type={}, paymentId={}",
                request.getType(), request.getData().getPaymentId());

        // V2 규격: 결제 완료 이벤트 타입 확인
        if ("Transaction.Paid".equals(request.getType())) {
            // data 안의 paymentId(우리 시스템의 merchant_uid 역할)를 사용
            subscriptionService.confirmSubscription(request.getData().getPaymentId());
        }

        return ResponseEntity.ok().build();
    }
}
