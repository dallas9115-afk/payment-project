package com.bootcamp.paymentdemo.security;

import com.bootcamp.paymentdemo.global.error.CommonError;
import com.bootcamp.paymentdemo.global.error.CommonException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    // JWT 비밀키
    @Value("${jwt.secret-key}")
    private String secretKey;
    private Key key;

    // 토큰 만료 시간 설정
    private final long TOKEN_EXPIRE_TIME = 24 * 60 * 60 * 1000L;

    @PostConstruct
    public void init() {
        key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    // JWT 토큰 생성
    public String createToken(Long customerId, String email) {
        Date date = new Date();

        return Jwts.builder()
                .setSubject(String.valueOf(customerId))
                .claim("email", email)
                .setIssuedAt(date)
                .setExpiration(new Date(date.getTime() + TOKEN_EXPIRE_TIME))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // JWT 토큰 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .setSigningKey(key)
                    .build()
                    .parseSignedClaims(token);
            return true;

        } catch (SecurityException | MalformedJwtException | SignatureException e) {
            log.error("JWT 토큰이 유효하지 않습니다.");
            throw new CommonException(CommonError.INVALID_TOKEN);

        } catch (ExpiredJwtException e) {
            log.error("JWT 토큰이 만료되었습니다.");
            throw new CommonException(CommonError.EXPIRED_TOKEN);

        } catch (UnsupportedJwtException e) {
            log.error("지원되지 않는 JWT 토큰입니다.");
            throw new CommonException(CommonError.UNSUPPORTED_TOKEN);

        } catch (IllegalArgumentException e) {
            log.error("JWT 토큰이 비어있습니다.");
            throw new CommonException(CommonError.EMPTY_TOKEN);
        }
    }

    // JWT 토큰 정보 추출
    public Claims getCustomerInfoFromToken(String token) {
        return Jwts.parser()
                .setSigningKey(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // JWT 토큰에서 이메일 추출
    public String getEmail(String token) {
        return getCustomerInfoFromToken(token)
                .get("email", String.class);
    }

}
