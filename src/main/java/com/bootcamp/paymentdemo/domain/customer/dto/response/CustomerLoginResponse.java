package com.bootcamp.paymentdemo.domain.customer.dto.response;

import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import lombok.Builder;

@Builder
public record CustomerLoginResponse(
        Long id,
        String name,
        String email,
        String phoneNumber
) {
    public static CustomerLoginResponse from(Customer customer) {
        return CustomerLoginResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .email(customer.getEmail())
                .phoneNumber(customer.getPhoneNumber())
                .build();
    }
}
