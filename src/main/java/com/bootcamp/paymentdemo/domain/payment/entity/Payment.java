package com.bootcamp.paymentdemo.domain.payment.entity;

import com.bootcamp.paymentdemo.domain.order.entity.Order;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentStatus;
import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "payments",
        indexes = {
                @Index(name = "idx_payments_order_deleted_created", columnList = "order_id,deleted_at,created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE payments SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Payment extends BaseEntity {


        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(nullable = false)
        private Order order;

        // 결제고유ID (포트원 요청/조회 키로사용)
        @Column(unique = true, nullable = false)
        private String paymentId;

        // 오더에있는 주문금액
        @Column(nullable = false)
        private Long orderAmount;

        @Column(nullable = false)
        private Long usePoint;
        //포인트를 뺀 총금액
        @Column(nullable = false)
        private Long pgAmount;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private PaymentStatus status;

        @Column
        private LocalDateTime paidAt;

        @Column
        private LocalDateTime expiresAt; // 결제창 그냥닫은경우 주문취소로 바꾸는용도
        @Column
        private LocalDateTime refundedAt;

        public static Payment of(
                Order order, Long totalAmount,String paymentId,Long usePoint) {

                Payment payment = new Payment();
                payment.order = order;
                payment.orderAmount = totalAmount;
                payment.usePoint = usePoint;
                payment.pgAmount = totalAmount - usePoint;
                payment.paymentId = paymentId;
                payment.status = PaymentStatus.READY;
                payment.expiresAt= LocalDateTime.now().plusMinutes(10);
                return payment;
        }


        public void confirm() {
                this.status = PaymentStatus.PAID;
                this.paidAt = LocalDateTime.now();
        }
        public void ready(){
                this.status = PaymentStatus.READY;
        }

        public void fail() {
                this.status = PaymentStatus.FAILED;
        }

        public void refund() {
                this.status = PaymentStatus.REFUNDED;
                this.refundedAt = LocalDateTime.now();
        }

        public boolean isRefundable() {
                return this.status == PaymentStatus.PAID;
        }

        public boolean isAlreadyProcessed() {
                return this.status == PaymentStatus.PAID ||
                        this.status == PaymentStatus.FAILED ||
                        this.status == PaymentStatus.REFUNDED ||
                        this.status == PaymentStatus.EXPIRED;
        }

        public boolean isRefunded() {
                return this.status == PaymentStatus.REFUNDED;
        }


        public void expire() {
                this.status = PaymentStatus.EXPIRED;
        }
}
