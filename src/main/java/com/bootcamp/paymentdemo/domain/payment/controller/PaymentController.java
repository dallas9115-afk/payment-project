package com.bootcamp.paymentdemo.domain.payment.controller;

import com.bootcamp.paymentdemo.domain.payment.dto.Request.PaymentCreateReadyRequest;
import com.bootcamp.paymentdemo.domain.payment.dto.Request.PortOneWebhookRequest;
import com.bootcamp.paymentdemo.domain.payment.dto.Response.PaymentConfirmResponse;
import com.bootcamp.paymentdemo.domain.payment.dto.Response.PaymentCreateReadyResponse;
import com.bootcamp.paymentdemo.domain.payment.dto.Response.PaymentDetailResponse;
import com.bootcamp.paymentdemo.domain.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    // 현재 프론트엔드엔 어디에서도 결제조회를 하지않기때문에 서비스는 구현해놨지만 사용하진않음

    /**
     * 포트원 결제 결과 웹훅 수신 엔드포인트
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handlePortOneWebhook(
            @RequestHeader(value = "webhook-id", required = false) String webhookId,
            @RequestBody PortOneWebhookRequest request) {

        try {
            log.info(" 포트원 웹훅 수신 WebhookId: {}, EventType: {}, PaymentId: {}",
                    webhookId, request.getType(), request.getData().getPaymentId());

            // 비즈니스 로직(Service)으로 검증 및 상태 업데이트 위임
            paymentService.processWebhook(webhookId, request);

            // 정상 처리 시 포트원에 "성공적으로 수신함" 응답 (재시도 중단)
            return ResponseEntity.ok("Webhook Received");

        } catch (IllegalArgumentException e) {
            // 위조된 결제 등 비즈니스 규칙 위반 -> 더 이상 재시도 불필요
            log.warn("웹훅 처리 거부 (비즈니스 오류): {}", e.getMessage());
            return ResponseEntity.ok("Webhook Rejected");
        } catch (Exception e) {
            // DB 통신 장애 등 일시적 오류 -> 500 에러를 뱉어서 포트원이 나중에 재시도하게 함
            log.error("웹훅 처리 중 시스템 에러 발생: ", e);
            return ResponseEntity.internalServerError().body("Webhook Processing Failed");
        }    }

    @PostMapping("/v1/payments/checkout-ready")
    public ResponseEntity<PaymentCreateReadyResponse> checkoutReady(
            Authentication authentication,
            @RequestBody PaymentCreateReadyRequest request
    ) {
        PaymentCreateReadyResponse response = paymentService.create(authentication, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Client Confirm 엔드포인트
     *
     * 프론트가 PortOne 결제창 종료 후 paymentId를 들고 호출합니다.
     * 여기서는 paymentId만 받아서 Service의 "진짜 검증 로직"으로 넘기는 게 핵심입니다.
     */
    @PostMapping("/v1/payments/{paymentId}/confirm")
    public ResponseEntity<PaymentConfirmResponse> confirm(
            Authentication authentication,
            @PathVariable String paymentId
    ) {
        PaymentConfirmResponse response = paymentService.confirm(authentication, paymentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/v1/payments/{paymentId}")
    public ResponseEntity<PaymentDetailResponse> paymentDetail(
            Authentication authentication,
            @PathVariable String paymentId
    ) {
        PaymentDetailResponse response = paymentService.getPaymentDetail(authentication, paymentId);
        return ResponseEntity.ok(response);
    }

}

