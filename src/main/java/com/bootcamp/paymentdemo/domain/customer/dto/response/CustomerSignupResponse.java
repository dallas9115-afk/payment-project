package com.bootcamp.paymentdemo.domain.customer.dto.response;

import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.repository.CustomerRepository;
import lombok.Builder;

@Builder
public record CustomerSignupResponse(
        Long id,
        String name,
        String email,
        String phoneNumber
) {
    public static CustomerSignupResponse from(Customer customer) {
        return CustomerSignupResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .email(customer.getEmail())
                .phoneNumber(customer.getPhoneNumber())
                .build();
    }
}
