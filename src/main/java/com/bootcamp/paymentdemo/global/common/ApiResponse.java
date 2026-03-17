package com.bootcamp.paymentdemo.global.common;

import com.bootcamp.paymentdemo.global.error.CommonError;

public record ApiResponse<T> (
        boolean success,
        T data,
        BaseErrorResponse error
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> fail(BaseErrorResponse errorResponse) {
        return new ApiResponse<>(false, null, errorResponse);
    }

    public static ApiResponse<Void> fail(CommonError error) {
        return fail(new BaseErrorResponse(
                error.getStatus().value(),
                error.getErrorCode(),
                error.getMessage()
        ));
    }

    public static ApiResponse<Void> fail(int status, String errorCode, String message) {
        return new ApiResponse<>(
                false, null, new BaseErrorResponse(status, errorCode, message)
        );
    }
}
