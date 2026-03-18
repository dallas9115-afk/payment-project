package com.bootcamp.paymentdemo.domain.payment.dto.Response;

import java.util.List;

public record PaymentCreateReadyResponse(
        boolean success,
        String status,
        String storeId,
        String channelKey,
        String paymentId,
        String orderName,
        Long totalAmount,
        String currency,
        String payMethod,
        Customer customer,
        String redirectUrl,
        List<String> noticeUrls
) {
    private static final String DEFAULT_CURRENCY = "KRW";
    private static final String DEFAULT_PAY_METHOD = "CARD";
    private static final String DEFAULT_REDIRECT_URL = "";

    public static PaymentCreateReadyResponse checkoutReady(
            String storeId,
            String channelKey,
            String paymentId,
            String orderName,
            Long totalAmount,
            Customer customer
    ) {
        return new PaymentCreateReadyResponse(
                true,
                "READY",
                storeId,
                channelKey,
                paymentId,
                orderName,
                totalAmount,
                DEFAULT_CURRENCY,
                DEFAULT_PAY_METHOD,
                customer,
                DEFAULT_REDIRECT_URL,
                List.of()
        );
    }

    public record Customer(
            String customerId,
            String fullName,
            String phoneNumber,
            String email
    ) {
        public static Customer of(
                String customerId,
                String fullName,
                String phoneNumber,
                String email
        ) {
            return new Customer(customerId, fullName, phoneNumber, email);
        }
    }
}
