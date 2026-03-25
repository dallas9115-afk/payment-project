package com.bootcamp.paymentdemo.domain.auth.service;

public interface TokenService {
    void saveRefreshToken(Long accountId, String refreshToken);

    String getRefreshToken(Long accountId);

    void deleteRefreshToken(Long accountId);
}
