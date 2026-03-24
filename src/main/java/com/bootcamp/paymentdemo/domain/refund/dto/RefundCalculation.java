package com.bootcamp.paymentdemo.domain.refund.dto;

// 환불 시작 시 한 번 계산한 값을 결제취소/재시도까지 그대로 전달하기 위한 DTO
public record RefundCalculation(
        Long cancelAmount, // 취소금액
        Long recoverableEarnedPoints, // 회수할포인트
        Long unrecoverableEarnedPoints  // 부족한 포인트
) {
}
