package com.bootcamp.paymentdemo.config;

import com.bootcamp.paymentdemo.security.JwtAuthenticationFilter;
import com.bootcamp.paymentdemo.security.oauth.CustomOAuth2UserService;
import com.bootcamp.paymentdemo.security.oauth.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// ✅ Spring Boot 4.0 새 패키지 경로
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;

import static org.springframework.boot.security.autoconfigure.web.servlet.PathRequest.toStaticResources;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(toStaticResources().atCommonLocations()).permitAll() // 정적 리소스
                        .requestMatchers(HttpMethod.GET, "/", "/pages/**").permitAll() // 템플릿 페이지
                        .requestMatchers(
                                "/api/auth/v1/register",
                                "/api/auth/v1/login",
                                "/api/auth/v1/me"
                        ).permitAll() // 공개 인증 API
                        .requestMatchers("/api/public/**").permitAll() // 공개 API
                        .requestMatchers("/actuator/**").permitAll() // 헬스체크
                        .requestMatchers(
                                "/api/payments/webhook",
                                "/api/v1/webhooks/portone"
                        ).permitAll() // 포트원 결제 웹훅 API (인증 불필요)
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll() // OAuth2 로그인
                        .requestMatchers("/api/**").authenticated() // 나머지 API 인증 필요
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        .successHandler(oAuth2SuccessHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}