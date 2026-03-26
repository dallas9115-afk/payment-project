package com.bootcamp.paymentdemo.domain.point.repository;

import com.bootcamp.paymentdemo.domain.point.entity.PointDetail;
import com.bootcamp.paymentdemo.domain.point.entity.PointStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface PointDetailRepository extends JpaRepository<PointDetail, Long> {


    List<PointDetail> findAllByOrderId(String orderId);

    List<PointDetail> findAllByCustomerIdAndRemainAmountGreaterThanOrderByExpiredAtAsc(Long userId, int i);

    boolean existsByOrderId(String orderId);

    boolean existsByOrderIdAndStatus(String orderId, PointStatus status);

    Slice<PointDetail> findAllByExpiredAtBeforeAndRemainAmountGreaterThan(
            LocalDateTime dateTime,
            int amount,
            Pageable pageable
    );

    @Query("SELECT SUM(p.remainAmount) FROM PointDetail p WHERE p.customerId = :customerId")
    Long sumRemainAmountByCustomerId(Long customerId);
}
