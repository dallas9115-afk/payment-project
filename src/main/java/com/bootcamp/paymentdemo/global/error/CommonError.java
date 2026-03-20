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
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "C2003", "존재하지 않는 고객입니다."),


    // [ 3000: JWT ]
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "J3001", "유효하지 않는 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "J3002", "사용 기간이 만료된 토큰입니다."),
    UNSUPPORTED_TOKEN(HttpStatus.UNAUTHORIZED, "J3003", "지원하지 않는 토큰 형식 입니다."),
    EMPTY_TOKEN(HttpStatus.UNAUTHORIZED, "J3004", "토큰값이 비어있습니다."),

    // [ 4000: POINT ]
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "P4001", "포인트 잔액이 부족합니다."),

    // [ 5000: ORDER ]
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "O5001", "주문 내역을 찾을 수 없습니다."),

    // [ 6000: PAYMENT ]
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "P6001", "존재하지 않는 결제 건입니다."),
    PAYMENT_ALREADY_COMPLETED(HttpStatus.CONFLICT, "P6002", "이미 결제 완료 처리된 주문입니다."),

    // [ 7000: PRODUCT ]
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "P7001", "해당 상품은 존재하지 않는 상품입니다.");





    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
