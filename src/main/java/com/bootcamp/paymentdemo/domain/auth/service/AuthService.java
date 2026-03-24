package com.bootcamp.paymentdemo.domain.auth.service;

import com.bootcamp.paymentdemo.domain.auth.dto.*;

public interface AuthService {

    RegisterResponseDto register(RegisterRequestDto request);

    LoginResult login(LoginRequestDto request);

    RefreshResponse refresh(String refreshToken);

    void logout(String refreshToken);
}
