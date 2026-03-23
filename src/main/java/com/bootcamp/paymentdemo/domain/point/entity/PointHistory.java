package com.bootcamp.paymentdemo.domain.point.entity;


import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.order.entity.Order;
import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.*;
import jakarta.persistence.Entity;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "point_histories")
public class PointHistory extends BaseEntity {
    // 로그 기록, 데이터 정합성 검증, Cs대응용

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    // 포인트 이력 연결 : 해당 이력이 어떤 포인트 에서 발생했는지 확인
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_detail_id")
    private PointDetail pointDetail;

    // 상세 주문 확인
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // 변동 종류(사용, 적립)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointType type;

    // 포인트의 변동액 기록
    @Column(nullable = false)
    private Long amount;   // 정합성을 위해 long 사용

    // 변동전 금액 : 사고 발생 시 사고 발생 시점으로 복구하기 위함
    @Column(nullable = false)
    private Long beforePoint;

    // 변동 직후의 최종 잔액을 기록 <- 정합성 검증에 사용
    @Column(nullable = false)
    private Long afterPoint;

    // 주문번호
    @Column(name = "order_no")
    private String orderId;

    // 변동 사유
    @Column
    private String reason;

    @Builder
    public PointHistory(Customer customer, // 파라미터명도 변경
                        PointDetail pointDetail,
                        Order order,
                        PointType type,
                        Long amount,
                        Long beforePoint,
                        Long afterPoint,
                        String orderId,
                        String reason) {
        this.customer = customer;
        this.pointDetail = pointDetail;
        this.order = order;
        this.type = type;
        this.amount = amount;
        this.beforePoint = beforePoint;
        this.afterPoint = afterPoint;
        this.orderId = orderId;
        this.reason = reason;


    }
}
