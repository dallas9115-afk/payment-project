package com.bootcamp.paymentdemo.domain.point.entity;


import com.bootcamp.paymentdemo.global.common.BaseEntity;
import com.bootcamp.paymentdemo.global.error.CommonError;
import com.bootcamp.paymentdemo.global.error.CommonException;
import jakarta.persistence.*;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "point_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointDetail extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer initialAmount;  // 최초 포인트 잔액 0원 처리

    @Column(nullable = false)
    private Integer remainAmount;  // 현재 포인트 잔액

    @Column(nullable = false)
    private LocalDateTime expiredAt;  //만료일

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PointStatus status;

    public PointDetail(Long userId, Integer amount) {
        this.userId = userId;
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


}

