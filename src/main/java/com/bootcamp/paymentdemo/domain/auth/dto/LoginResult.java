package com.bootcamp.paymentdemo.domain.auth.dto;

public record LoginResult(
        String accessToken,
        String refreshToken
) {
}
