package com.bootcamp.paymentdemo.domain.subscription2.dto;

public record BillingContext(
        Long billingId,
        Long subscriptionId,
        String billingKey,
        Long amount,
        String customerId
) {}
