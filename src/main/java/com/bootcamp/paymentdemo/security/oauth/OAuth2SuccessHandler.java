package com.bootcamp.paymentdemo.security.oauth;

import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.repository.CustomerRepository; // Repository 직접 주입
import com.bootcamp.paymentdemo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder; // PasswordEncoder 추가

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    // CustomerService 대신 Repository와 PasswordEncoder를 사용합니다.
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        // 구글과 카카오의 이메일 응답 구조가 다를 수 있으므로 안전하게 가져옵니다.
        String email = "";
        String name = "전사"; // 기본 이름

        if (oAuth2User.getAttributes().containsKey("email")) {
            email = (String) oAuth2User.getAttributes().get("email"); // 구글
        } else if (oAuth2User.getAttributes().containsKey("kakao_account")) {
            // 카카오
            java.util.Map<String, Object> kakaoAccount = (java.util.Map<String, Object>) oAuth2User.getAttributes().get("kakao_account");
            if (kakaoAccount.containsKey("email")) {
                email = (String) kakaoAccount.get("email");
            }
        }

        // 이메일이 없으면 임시 이메일 생성 (카카오 동의 안 했을 경우 대비)
        if (email.isEmpty()) {
            email = oAuth2User.getName() + "@social.sparta.com";
        }

        final String finalEmail = email;

// DB에서 찾고, 없으면 그 자리에서 바로 생성해서 저장합니다. (CUSTOMER_NOT_FOUND 예외 원천 차단)
        Customer customer = customerRepository.findByEmail(finalEmail)
                .orElseGet(() -> customerRepository.save(
                        Customer.builder()
                                .email(finalEmail)
                                .name(name)
                                .password(passwordEncoder.encode("social_login_dummy_password")) // 소셜 로그인은 비번이 불필요하므로 더미값
                                .phoneNumber("010-0000-0000") // 필수값이면 더미값
                                .currentPoint(0L)
                                .rank(com.bootcamp.paymentdemo.domain.customer.enums.Rank.NORMAL)
                                .build()
                ));

        String token = jwtTokenProvider.generateAccessToken(customer.getId());

        // 상대 경로 리다이렉트 유지
        String targetUrl = "/?token=" + token;
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}