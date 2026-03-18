package com.bootcamp.paymentdemo.domain.point.repository;

import com.bootcamp.paymentdemo.domain.point.entity.PointDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointDetailRepository extends JpaRepository<PointDetail, Long> {


    List<PointDetail> findAllByUserIdAndRemainAmountGreaterThanOrderByExpiredAtAsc(Long userId, int i);
}
