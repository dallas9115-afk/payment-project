package com.bootcamp.paymentdemo.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonError {

    // -- 1000:  --
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,"S1001","서버 내부 오류가 발생하였습니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
