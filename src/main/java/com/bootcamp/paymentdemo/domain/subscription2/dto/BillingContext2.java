package com.bootcamp.paymentdemo.domain.subscription2.dto;

public record BillingContext2(
        Long billingId,
        Long subscriptionId,
        String billingKey,
        Long amount,
        Long customerId
) {}
