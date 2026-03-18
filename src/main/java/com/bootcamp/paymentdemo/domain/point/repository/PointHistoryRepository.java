package com.bootcamp.paymentdemo.domain.point.repository;

import com.bootcamp.paymentdemo.domain.point.entity.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {
}
