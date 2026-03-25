package com.bootcamp.paymentdemo.global.common.filter;

import jakarta.servlet.*; // Filter, ServletRequest, ServletResponse 등 (자바 표준)
import org.slf4j.MDC; // 로깅 추적용 (SLF4J)
import org.springframework.core.Ordered; // 필터 순서 결정용
import org.springframework.core.annotation.Order; // 필터 순서 결정용
import org.springframework.stereotype.Component; // 스프링 빈 등록용

import java.io.IOException;
import java.util.UUID;
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // 모든 필터 중 가장 먼저 실행
public class LoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // 1. 모든 API 요청에 대해 공통 TraceId 생성
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            // 2. 요청이 끝나면 알아서 청소 (컨트롤러에서 MDC.clear() 삭제 가능!)
            MDC.clear();
        }
    }
}
