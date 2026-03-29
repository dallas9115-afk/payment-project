package com.bootcamp.paymentdemo.domain.subscription.controller;

import com.bootcamp.paymentdemo.domain.payment.dto.Request.PortOneWebhookRequest;
import com.bootcamp.paymentdemo.domain.subscription.service.SubscriptionService;
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
public class SubscriptionWebhookController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/portone")
    public ResponseEntity<Void> handlePortOneWebhook(@RequestBody PortOneWebhookRequest request) {
        log.info("포트원 V2 구독 웹훅 수신: paymentId={}, status={}, txId={}",
                request.getPaymentId(), request.getStatus(), request.getTxId());

        // V2 규격: 결제 완료(Paid) 상태일 때만 구독 확정
        if (request.isPaidStatus()) {
            subscriptionService.confirmSubscription(request.getPaymentId());
        }

        return ResponseEntity.ok().build();
    }
}
