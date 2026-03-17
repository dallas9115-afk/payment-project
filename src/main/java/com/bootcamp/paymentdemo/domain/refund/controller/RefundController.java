package com.bootcamp.paymentdemo.domain.refund.controller;

import com.bootcamp.paymentdemo.domain.refund.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payment")
public class RefundController {

    private final RefundService refundService;

}
