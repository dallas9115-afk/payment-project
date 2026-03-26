package com.bootcamp.paymentdemo.global.error;

import com.bootcamp.paymentdemo.global.common.dto.ApiResponse;
import com.bootcamp.paymentdemo.global.common.dto.BaseErrorResponse;
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

//    // 테스트
//    @ExceptionHandler(IllegalArgumentException.class)
//    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
//        log.warn("Illegal argument", e);
//        return ResponseEntity
//                .status(HttpStatus.BAD_REQUEST)
//                .body(ApiResponse.fail(
//                        HttpStatus.BAD_REQUEST.value(),
//                        "BAD_REQUEST",
//                        e.getMessage()
//                ));
//    }
//// 테스트
//    @ExceptionHandler(IllegalStateException.class)
//    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException e) {
//        log.warn("Illegal state", e);
//        return ResponseEntity
//                .status(HttpStatus.CONFLICT)
//                .body(ApiResponse.fail(
//                        HttpStatus.CONFLICT.value(),
//                        "CONFLICT",
//                        e.getMessage()
//                ));
//    }

    // Unexpected Error
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected exception: {} - {}", e.getClass().getName(), e.getMessage(), e);
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


//    // 400: 잘못된 요청 (DTO @Valid 검증 실패 등)
//    @ExceptionHandler(MethodArgumentNotValidException.class)
//    public ResponseEntity<BaseErrorResponse> handleValidation(MethodArgumentNotValidException e) {
//        log.warn("Validation error: {}", e.getMessage());
//        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
//                .body(new BaseErrorResponse(
//                        HttpStatus.BAD_REQUEST.value(), // status: 400
//                        "INVALID_INPUT",               // errorCode
//                        "입력값이 올바르지 않습니다."      // message
//                ));
//    }
//
//    // 409: 비즈니스 로직 충돌 (이미 결제 진행 중, 상태 불일치 등)
//    @ExceptionHandler(IllegalStateException.class)
//    public ResponseEntity<BaseErrorResponse> handleIllegalState(IllegalStateException e) {
//        log.warn("Business logic error: {}", e.getMessage());
//        return ResponseEntity.status(HttpStatus.CONFLICT)
//                .body(new BaseErrorResponse(
//                        HttpStatus.CONFLICT.value(),    // status: 409
//                        "BUSINESS_ERROR",              // errorCode
//                        e.getMessage()                  // message (서비스에서 던진 메시지 그대로)
//                ));
//    }
//
//    // 500: 기타 모든 예외
//    @ExceptionHandler(Exception.class)
//    public ResponseEntity<BaseErrorResponse> handleAll(Exception e) {
//        log.error("Unexpected error occurred: ", e); // 에러 로그를 남겨야 추적이 됩니다!
//        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body(new BaseErrorResponse(
//                        HttpStatus.INTERNAL_SERVER_ERROR.value(), // status: 500
//                        "SERVER_ERROR",                           // errorCode
//                        "서버 내부 오류가 발생했습니다."              // message
//                ));
//    }
}