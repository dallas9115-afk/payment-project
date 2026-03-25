package com.bootcamp.paymentdemo.domain.auth.dto;

import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import lombok.Builder;

import java.util.Date;

@Builder
public record CurrentCustomerResponseDto(
        Long customerId,
        String email,
        String name,
        String phoneNumber,
        Date createdAt
) {
    public static CurrentCustomerResponseDto from(Customer customer) {
        return CurrentCustomerResponseDto.builder()
                .customerId(customer.getId())
                .email(customer.getEmail())
                .name(customer.getName())
                .phoneNumber(customer.getPhoneNumber())
                .createdAt(customer.getCreatedAt())
                .build();
    }
}
