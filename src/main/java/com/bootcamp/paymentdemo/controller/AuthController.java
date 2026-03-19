package com.bootcamp.paymentdemo.controller;

import com.bootcamp.paymentdemo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * 인증 관련 API 컨트롤러
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 로그인 API
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        Map<String, Object> response = new HashMap<>();

        try {
            // 1. 인증 시도
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );

            // 2. JWT 토큰 생성
            String token = jwtTokenProvider.createToken(email);

            // 3. 응답
            response.put("success", true);
            response.put("email", email);

            return ResponseEntity.ok()
                    .header("Authorization", "Bearer " + token)
                    .body(response);

        } catch (AuthenticationException e) {
            response.put("success", false);
            response.put("message", "이메일 또는 비밀번호가 올바르지 않습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    /**
     * 현재 로그인한 사용자 정보 조회 API
     * GET /api/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Principal principal) {

        String email = principal.getName();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("email", email);
        response.put("customerUid", "CUST_" + Math.abs(email.hashCode()));
        response.put("name", email.split("@")[0]);
        response.put("phone", "010-0000-0000");
        response.put("pointBalance", 1000L);

        return ResponseEntity.ok(response);
    }
}