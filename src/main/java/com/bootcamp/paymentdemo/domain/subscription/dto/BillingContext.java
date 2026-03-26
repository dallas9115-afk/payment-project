package com.bootcamp.paymentdemo.domain.subscription.dto;

public record BillingContext(
        Long billingId,
        Long subscriptionId,
        String billingKey,
        Long amount,
        Long customerId
) {}
