package com.bootcamp.paymentdemo.domain.auth.dto.response;

public record RegisterResponseDto(
        Long id,
        String name,
        String email,
        String phoneNumber
) {
}