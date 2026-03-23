package com.bootcamp.paymentdemo.domain.auth.dto.response;

public record LoginResponseDto(
        String token,
        Long id,
        String name,
        String email,
        String phoneNumber
) {
}