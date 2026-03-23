package com.bootcamp.paymentdemo.domain.point.entity;


import com.bootcamp.paymentdemo.global.common.BaseEntity;
import com.bootcamp.paymentdemo.global.error.CommonError;
import com.bootcamp.paymentdemo.global.error.CommonException;
import jakarta.persistence.*;
import jakarta.persistence.Entity;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "point_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class PointDetail extends BaseEntity {
    // 포인트 조각 관리 <- 유효기관 관리, 잔액 추적, 데이터 보존

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소유 유저
    @Column(nullable = false)
    private Long customerId;

    // 주문번호
    private String orderId;

    // 최초 적립액[환불 시 전액 복구 여부를 판단하는 기준점
    // 초과 복구 방지
    // 전체 포인트 발행액 확인 <- 원본성 유지
    @Column(nullable = false)
    private Integer initialAmount;  // 최초 포인트 잔액 0원 처리

    // 유효기간 내에 실제로 가능한 포인트
    @Column(nullable = false)
    private Integer remainAmount;  // 현재 포인트 잔액

    // 포인트 소멸 시점(FIFO로 차감)
    @Column(nullable = false)
    private LocalDateTime expiredAt;  //만료일

    //포인트 상태(ACCUMULATED, COMPLETED)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PointStatus status;

    public PointDetail(Long customerId, Integer amount) {
        this.customerId = customerId;
        this.initialAmount = amount;
        this.remainAmount = amount;
        this.expiredAt = LocalDateTime.now().plusYears(1); //적립 시점부터 1년뒤
        this.status = PointStatus.ACCUMULATED;
    }

    // 환불시 사용할 메서드임
    public void cancelUsage(Integer amountToRestore) {
        this.remainAmount += amountToRestore;
        this.status = (this.remainAmount.equals(this.initialAmount))
                ? PointStatus.ACCUMULATED : PointStatus.PARTIALLY_USED;
    }


    // 포인트 결제시
    public void use(Integer amountToUse){
        if (this.remainAmount < amountToUse) {
            throw new CommonException((CommonError.INSUFFICIENT_BALANCE));
        }

        this.remainAmount -= amountToUse;

        //잔액에 따라 상태 자동 변경
        if(this.remainAmount == 0) {
            this.status = PointStatus.COMPLETED;
        } else {
            this.status = PointStatus.PARTIALLY_USED;
        }

    }

    public void cancel() {
        this.status = PointStatus.CANCELED;
        this.remainAmount = 0;

    }


}

