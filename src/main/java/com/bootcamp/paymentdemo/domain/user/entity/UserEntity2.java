package com.bootcamp.paymentdemo.domain.user.entity;


import com.bootcamp.paymentdemo.domain.point.entity.PointDetail;
import com.bootcamp.paymentdemo.domain.point.entity.PointHistory;
import com.bootcamp.paymentdemo.domain.point.entity.PointStatus;
import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.*;
import jakarta.persistence.Entity;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "customers")
public class UserEntity2 extends BaseEntity {

    // 임호진이가 임시로 구현한 엔티티임 for SnapShot-------------
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;

    // 포인트 잔액 스냅샷
    @Column(nullable = false)
    private Long currentPoint = 0L;

    // 잔액 업데이트 메서드
    public void deductPoint(Long amount) {
        if(amount <= 0) {
            throw new IllegalArgumentException("차감 금액은 0보다 커야 합니다.");
        }

        if(this.currentPoint < amount){
            throw new IllegalStateException("잔액이 부족합니다. ( 현재: " + this.currentPoint + ")");
        }
        this.currentPoint -= amount;
    }

    public void addPoint(Long amount) {

        if(amount <= 0) {
            throw new IllegalArgumentException("적립 금액은 0보다 커야 합니다.");
        }
        this.currentPoint += amount;
    }

    // 스냅샷 메서드
    public PointHistory deductPointWithDetail(PointDetail detail,
                                              Long amountToDeduct,
                                              String orderId,
                                              PointStatus type,
                                              String reason) {

        // 1. 사전 검증
        if (this.currentPoint < amountToDeduct) {
            throw new IllegalStateException("잔액이 부족합니다.");
        }

        Long before = this.currentPoint;
        this.currentPoint -= amountToDeduct; // 2. 스냅샷 차감
        Long after = this.currentPoint;

        // 3. PointHistory의 빌더를 호출
        return PointHistory.builder()
                .user(this)
                .pointDetail(detail)
                .amount(-amountToDeduct)
                .beforePoint(before)
                .afterPoint(after)
                .type(type)
                .orderId(orderId)
                .reason(reason)
                .build();
    }
}
