package com.bootcamp.paymentdemo.domain.auth.controller;

import com.bootcamp.paymentdemo.domain.auth.dto.request.LoginRequestDto;
import com.bootcamp.paymentdemo.domain.auth.dto.request.RegisterRequestDto;
import com.bootcamp.paymentdemo.domain.auth.dto.response.LoginResponseDto;
import com.bootcamp.paymentdemo.domain.auth.dto.response.RegisterResponseDto;
import com.bootcamp.paymentdemo.domain.auth.service.AuthService;
import com.bootcamp.paymentdemo.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    @PostMapping("/v1/register")
    public ResponseEntity<ApiResponse<RegisterResponseDto>> register(@RequestBody RegisterRequestDto request) {
        return ResponseEntity.ok(
                ApiResponse.success(service.register(request))
        );
    }

    @PostMapping("/v1/login")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(@RequestBody LoginRequestDto request) {
        LoginResponseDto response = service.login(request);
        return ResponseEntity.status(HttpStatus.OK)
                .header("Authorization", "Bearer %s".formatted(response.token()))
                .body(ApiResponse.success(response));
    }
}