package com.bootcamp.paymentdemo.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final Map<Long, String> tokenStore = new ConcurrentHashMap<>();

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Override
    public void saveRefreshToken(Long accountId, String refreshToken) {
        tokenStore.put(accountId, refreshToken);
    }

    public String getRefreshToken(Long accountId) {
        return tokenStore.get(accountId);
    }

    public void deleteRefreshToken(Long accountId) {
        tokenStore.remove(accountId);
    }
}
