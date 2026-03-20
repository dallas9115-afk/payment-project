package com.bootcamp.paymentdemo.domain.payment.enums;

public enum PaymentRetryOperation {
    VERIFY_PAYMENT, // 결제확인재시도
    CANCEL_PAYMENT  // 결제 취소 재시도
}
