package com.bootcamp.paymentdemo.global.common.dto;

public record BaseErrorResponse(
        int status,
        String errorCode,
        String message
) {
}
