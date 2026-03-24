package com.bootcamp.paymentdemo.domain.auth.dto;

import java.util.Date;

public record RegisterResponseDto(
        Long id,
        String name,
        String email,
        String phoneNumber,
        Date createdAt
) {
}