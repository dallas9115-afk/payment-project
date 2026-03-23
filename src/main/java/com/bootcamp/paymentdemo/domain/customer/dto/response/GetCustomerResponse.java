package com.bootcamp.paymentdemo.domain.customer.dto.response;

public record GetCustomerResponse(
        Long id,
        String name,
        String email,
        String phoneNumber
) {
}