package com.bootcamp.paymentdemo.domain.point.entity;

import com.bootcamp.paymentdemo.domain.point.entity.PointType;
import jakarta.persistence.*;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "point_transactions") // 조장님의 테이블명 반영
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class) // 생성일 자동 기록
public class PointTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자 연결
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 주문 연결
    @Column(name = "order_id")
    private Long orderId;

    // 결제 연결
    @Column(name = "payment_id")
    private Long paymentId;

    // 포인트 변동 유형(Lock 연계)
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PointType type;

    // 변동된 포인트 수치
    @Column(name = "points", nullable = false)
    private Integer points;

    // 최종 잔액(정합성 체크)
    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    // 포인트 소멸 예정일
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // 거래 상세 설명
    @Column(name = "description")
    private String description;

    // 기록 생성 일시
    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public PointTransactionEntity(Long userId, Long orderId, Long paymentId, PointType type,
                                  Integer points, Integer balanceAfter, LocalDateTime expiresAt, String description) {
        this.userId = userId;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.type = type;
        this.points = points;
        this.balanceAfter = balanceAfter;
        this.expiresAt = expiresAt;
        this.description = description;
    }
}
