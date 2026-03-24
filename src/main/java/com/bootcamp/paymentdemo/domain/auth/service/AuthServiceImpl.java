package com.bootcamp.paymentdemo.domain.auth.service;

import com.bootcamp.paymentdemo.domain.auth.dto.*;
import com.bootcamp.paymentdemo.domain.customer.dto.request.CustomerSignupRequest;
import com.bootcamp.paymentdemo.domain.customer.dto.response.CustomerSignupResponse;
import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.service.CustomerService;
import com.bootcamp.paymentdemo.global.error.CommonError;
import com.bootcamp.paymentdemo.global.error.CommonException;
import com.bootcamp.paymentdemo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final CustomerService customerService;
    private final TokenService tokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Override
    public RegisterResponseDto register(RegisterRequestDto request) {
        CustomerSignupResponse response =
                customerService.create(
                        new CustomerSignupRequest(
                                request.name(),
                                request.email(),
                                request.password(),
                                request.phoneNumber()
                        )
                );

        return new RegisterResponseDto(
                response.id(),
                response.name(),
                response.email(),
                response.phoneNumber(),
                response.createdAt()
        );
    }

    @Override
    public RefreshResponse refresh(String refreshToken) {
        Long id = jwtTokenProvider.extractCustomerId(refreshToken);

        String savedRefreshToken = tokenService.getRefreshToken(id);

        if (savedRefreshToken == null || !savedRefreshToken.equals(refreshToken)) {
            throw new CommonException(CommonError.INVALID_TOKEN);
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(id);
        return new RefreshResponse(newAccessToken);
    }

    @Override
    public void logout(String refreshToken) {
        Long id = jwtTokenProvider.extractCustomerId(refreshToken);
        tokenService.deleteRefreshToken(id);
    }

    @Override
    public LoginResult login(LoginRequestDto request) {
        Customer customer = customerService.findCustomerByEmail(request.email());

        if (!passwordEncoder.matches(request.password(), customer.getPassword())) {
            throw new CommonException(CommonError.LOGIN_FAILED);
        }

        String accessToken = jwtTokenProvider.generateAccessToken(customer.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(customer.getId());

        tokenService.saveRefreshToken(customer.getId(), refreshToken);

        return new LoginResult(accessToken, refreshToken);
    }
}
