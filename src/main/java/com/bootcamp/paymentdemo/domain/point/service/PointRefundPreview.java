package com.bootcamp.paymentdemo.domain.point.service;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PointRefundPreview {
    private Long currentPointBalance;        // 현재 잔액
    private Long restorableUsedPoints;      // 복구될 사용 포인트 (사용 취소)
    private Long earnedPointsToCancel;      // 회수 대상 적립 포인트 (적립 취소)
    private Long unrecoverableEarnedPoints; // 회수 불가 포인트 (잔액 부족으로 현금 상계 필요 금액)
}
