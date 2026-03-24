package com.bootcamp.paymentdemo.domain.auth.dto;

import java.util.Date;

public record LoginResponseDto(
        String token,
        Long id,
        String name,
        String email,
        String phoneNumber
) {
    public record AccountInfo(
            String id,
            String email,
            Date createdAt
    ) {
    }
}