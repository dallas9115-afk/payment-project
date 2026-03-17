package com.bootcamp.paymentdemo.global.common;

public record BaseErrorResponse(
        int status,
        String errorCode,
        String message
) {
}
