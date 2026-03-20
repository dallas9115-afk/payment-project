package com.bootcamp.paymentdemo.domain.point.repository;

import com.bootcamp.paymentdemo.domain.point.entity.PointDetail;
import com.bootcamp.paymentdemo.domain.point.entity.PointType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PointDetailRepository extends JpaRepository<PointDetail, Long> {


    List<PointDetail> findAllByOrderId(String orderId);

    List<PointDetail> findAllByCustomerIdAndRemainAmountGreaterThanOrderByExpiredAtAsc(Long userId, int i);

    boolean existsByOrderId(String orderId);

    boolean existsByOrderIdAndType(String orderId, PointType type);

    Slice<PointDetail> findAllByAtBeforeAndRemainAmountGreaterThan(LocalDateTime now, int i, PageRequest of);

    Long sumRemainAmountByCustomerId(Long customerId);
}
