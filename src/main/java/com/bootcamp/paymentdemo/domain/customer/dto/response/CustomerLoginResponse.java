package com.bootcamp.paymentdemo.domain.customer.dto.response;

import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import lombok.Builder;

@Builder
public record CustomerLoginResponse(
        Long id,
        String name,
        String email,
        String token
) {
    public static CustomerLoginResponse from(Customer customer, String token) {
        return CustomerLoginResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .email(customer.getEmail())
                .token(token)
                .build();
    }
}
