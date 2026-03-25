package com.bootcamp.paymentdemo.domain.auth.controller;

import com.bootcamp.paymentdemo.domain.auth.dto.*;
import com.bootcamp.paymentdemo.domain.auth.service.AuthService;
import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.service.CustomerService;
import com.bootcamp.paymentdemo.global.error.CommonError;
import com.bootcamp.paymentdemo.global.error.CommonException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/v1")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final AuthService authService;
    private final CustomerService customerService;

    @Value("${jwt.refresh-token-cookie-max-age}")
    private long refreshTokenCookieMaxAge;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDto> register(@RequestBody @Valid RegisterRequestDto request) {
        RegisterResponseDto customer = authService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponseDto(
                        customer.id(),
                        customer.name(),
                        customer.email(),
                        customer.phoneNumber(),
                        customer.createdAt()
                ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequestDto request,
                                   HttpServletResponse response) {
        LoginResult result = authService.login(request);

        jakarta.servlet.http.Cookie refreshCookie = new jakarta.servlet.http.Cookie(REFRESH_TOKEN_COOKIE, result.refreshToken());
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(true);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge((int) refreshTokenCookieMaxAge);
        response.addCookie(refreshCookie);

        return ResponseEntity.ok()
                .header("Authorization", "Bearer " + result.accessToken())
                .body(Map.of("success", true, "email", request.email()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<String> refresh(@CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new CommonException(CommonError.EMPTY_TOKEN);
        }
        RefreshResponse refreshResponse = authService.refresh(refreshToken);

        return ResponseEntity.ok(refreshResponse.accessToken());
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(
            @AuthenticationPrincipal Long customerId) {

        Customer customer = customerService.findById(customerId);

        return ResponseEntity.ok(CurrentCustomerResponseDto.from(customer));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = AuthController.REFRESH_TOKEN_COOKIE, required = false) String refreshToken, HttpServletResponse response) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        jakarta.servlet.http.Cookie deleteCookie = new jakarta.servlet.http.Cookie(REFRESH_TOKEN_COOKIE, "");
        deleteCookie.setHttpOnly(true);
        deleteCookie.setSecure(true);
        deleteCookie.setPath("/");
        deleteCookie.setMaxAge(0);
        response.addCookie(deleteCookie);

        return ResponseEntity.ok().build();
    }
}