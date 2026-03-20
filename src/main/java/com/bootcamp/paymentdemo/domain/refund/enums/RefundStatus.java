package com.bootcamp.paymentdemo.domain.refund.enums;

public enum RefundStatus {
    REQUESTED, // 환불요청
    RETRYING,  // 취소 진행중
    REFUNDED,  // 환불완료
    FAILED,    // 실패
    CANCEL_REJECTED,
    PARTIAL_REFUNDED
}
