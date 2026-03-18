package com.bootcamp.paymentdemo.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonError {

    // [ 1000: SERVER ]
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,"S1001","서버 내부 오류가 발생하였습니다."),

    // [ 2000: CUSTOMER ]
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "C2001", "이미 존재하는 사용자 이메일입니다."),
    LOGIN_FAILED(HttpStatus.BAD_REQUEST, "C2002", "계정 정보 또는 비밀번호가 일치하지 않습니다."),

    // [ 3000: JWT ]
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, 3001, "유효하지 않는 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, 3002, "사용 기간이 만료된 토큰입니다."),
    UNSUPPORTED_TOKEN(HttpStatus.UNAUTHORIZED, 3003, "지원하지 않는 토큰 형식 입니다."),
    EMPTY_TOKEN(HttpStatus.UNAUTHORIZED, 3004, "토큰값이 비어있습니다."),

    // [ 4000: POINT ]
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "P4001", "포인트 잔액이 부족합니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
