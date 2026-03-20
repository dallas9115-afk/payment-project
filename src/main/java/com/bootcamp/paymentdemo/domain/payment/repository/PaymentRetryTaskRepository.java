package com.bootcamp.paymentdemo.domain.payment.repository;

import com.bootcamp.paymentdemo.domain.payment.enums.PaymentRetryOperation;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentRetryStatus;
import com.bootcamp.paymentdemo.domain.payment.entity.PaymentRetryTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface PaymentRetryTaskRepository extends JpaRepository<PaymentRetryTask, Long> {

    @Query("""
    select distinct t.paymentId
    from PaymentRetryTask t
    where t.paymentId in :paymentIds
      and t.operation = :operation
      and t.status in :statuses
""")
    Set<String> findPaymentIdsByPaymentIdInAndOperationAndStatusIn(
            @Param("paymentIds") Collection<String> paymentIds,
            @Param("operation") PaymentRetryOperation operation,
            @Param("statuses") Collection<PaymentRetryStatus> statuses
    );

    List<PaymentRetryTask> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            PaymentRetryStatus status,
            LocalDateTime now
    );

    boolean existsByPaymentIdAndOperationAndStatusIn(
            String paymentId,
            PaymentRetryOperation operation,
            Collection<PaymentRetryStatus> statuses
    );
}
