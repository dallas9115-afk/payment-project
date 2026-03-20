package com.bootcamp.paymentdemo.domain.refund.controller;

import com.bootcamp.paymentdemo.domain.refund.dto.Request.RefundRequest;
import com.bootcamp.paymentdemo.domain.refund.dto.Response.RefundResponse;
import com.bootcamp.paymentdemo.domain.refund.dto.Response.RefundSummaryResponse;
import com.bootcamp.paymentdemo.domain.refund.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class RefundController {

    private final RefundService refundService;


    @PostMapping("/v1/payments/{paymentId}/cancel")
    public ResponseEntity<RefundResponse> cancelPayment(
            Authentication authentication,
            @PathVariable("paymentId") String paymentId,
            @Valid @RequestBody RefundRequest request
    ) {
        RefundResponse response = refundService.cancel(authentication, paymentId,request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/v1/refunds/{paymentId}")
    public ResponseEntity<RefundSummaryResponse> getRefund(
            Authentication authentication,
            @PathVariable("paymentId") String paymentId
    ){
        RefundSummaryResponse response = refundService.getRefund(authentication, paymentId);
        return ResponseEntity.ok(response);
    }

}
