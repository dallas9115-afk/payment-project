package com.bootcamp.paymentdemo.domain.point.repository;

import com.bootcamp.paymentdemo.domain.point.entity.PointHistory;
import com.bootcamp.paymentdemo.domain.point.entity.PointType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

    List<PointHistory> findAllByOrderIdAndType(String orderId, PointType pointType);

    boolean existsByOrderIdAndType(String orderId, PointType type);

}
