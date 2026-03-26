package com.bootcamp.paymentdemo.global.error;

import com.bootcamp.paymentdemo.global.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Validation Error
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidError(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", errorMessage
                ));
    }

    // 테스트
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal argument", e);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        HttpStatus.BAD_REQUEST.value(),
                        "BAD_REQUEST",
                        e.getMessage()
                ));
    }
// 테스트
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException e) {
        log.warn("Illegal state", e);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(
                        HttpStatus.CONFLICT.value(),
                        "CONFLICT",
                        e.getMessage()
                ));
    }

    // Unexpected Error
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected exception",e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(CommonError.INTERNAL_SERVER_ERROR));
    }

    // Custom Error
    @ExceptionHandler(CommonException.class)
    public ResponseEntity<ApiResponse<Void>> handleException(CommonException e) {
        CommonError errorCode = e.getCommonError();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode));
    }
}