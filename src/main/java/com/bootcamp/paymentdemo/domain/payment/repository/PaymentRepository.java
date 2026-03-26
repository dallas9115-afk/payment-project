package com.bootcamp.paymentdemo.domain.payment.repository;

import com.bootcamp.paymentdemo.domain.order.entity.Order;
import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPaymentId(String paymentId);

    /**
     * 결제 확정 시 동시성 제어를 위해 비관적 락을 사용합니다.
     * - 같은 paymentId로 동시에 confirm 요청이 들어와도 한 트랜잭션만 선점 처리
     * - 중복 확정(이중 처리) 위험을 줄입니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findWithLockByPaymentId(String paymentId);


    List<Payment> findByStatusAndExpiresAtLessThanEqual(
            PaymentStatus status,
            LocalDateTime now
    );


    Optional<Payment> findTopByOrderOrderByCreatedAtDesc(Order order);

    Optional<Payment> findTopByOrderAndStatusInOrderByCreatedAtDesc(
            Order order,
            Collection<PaymentStatus> statuses
    );

}
