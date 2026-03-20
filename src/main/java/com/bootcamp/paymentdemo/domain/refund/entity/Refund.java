package com.bootcamp.paymentdemo.domain.refund.entity;

import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.refund.enums.RefundStatus;
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
        name = "refunds",
        indexes = {
                @Index(name = "idx_refunds_payment_id_deleted_created", columnList = "payment_id,deleted_at,created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE refunds SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Refund extends BaseEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "payment_id", nullable = false,unique = true)
        private Payment payment;

        @Column(nullable = false)
        private Long refundAmount;

        @Column(nullable = false)
        private String reason;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private RefundStatus status;

        @Column(nullable = false)
        private LocalDateTime processedAt;

        public static Refund createRequested(Payment payment, Long refundAmount, String reason) {
                Refund refund = new Refund();
                refund.payment = payment;
                refund.refundAmount = refundAmount;
                refund.reason = reason;
                refund.status = RefundStatus.REQUESTED;
                refund.processedAt = LocalDateTime.now();
                return refund;
        }

        public static Refund createRefunded(Payment payment, Long refundAmount, String reason) {
                Refund refund = new Refund();
                refund.payment = payment;
                refund.refundAmount = refundAmount;
                refund.reason = reason;
                refund.status = RefundStatus.REFUNDED;
                refund.processedAt = LocalDateTime.now();
                return refund;
        }

        public void markRetrying() {
                this.status = RefundStatus.RETRYING;
                this.processedAt = LocalDateTime.now();
        }

        public void markRefunded() {
                this.status = RefundStatus.REFUNDED;
                this.processedAt = LocalDateTime.now();
        }

        public void markFailed() {
                this.status = RefundStatus.FAILED;
                this.processedAt = LocalDateTime.now();
        }
}
