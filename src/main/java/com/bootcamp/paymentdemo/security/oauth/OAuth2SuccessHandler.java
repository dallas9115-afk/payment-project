package com.bootcamp.paymentdemo.security.oauth;

import com.bootcamp.paymentdemo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

// 🌟 Spring Boot 4.0 (Jakarta EE 11) 필수 패키지
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = (String) oAuth2User.getAttributes().get("email");
        String token = jwtTokenProvider.createToken(email);

        // 프론트엔드로 리다이렉트
        String targetUrl = "https://15.164.23.193.nip.io/?token=" + token;
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}