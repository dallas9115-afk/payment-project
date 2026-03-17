package com.bootcamp.paymentdemo.domain.refund.service;

import com.bootcamp.paymentdemo.domain.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository refundRepository;

}
