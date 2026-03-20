package com.bootcamp.paymentdemo.global.error;

import lombok.Getter;

/**
 * PortOne 통신 예외
 *
 * retryable=true  : 네트워크 타임아웃, 5xx 등 일시 장애 -> 재시도 대상
 * retryable=false : 4xx 비즈니스 오류 등 영구 실패 -> 재시도 비대상
 */
@Getter
public class PortOneApiException extends RuntimeException {
    private final boolean retryable;

    public PortOneApiException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }
}
