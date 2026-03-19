package com.bootcamp.paymentdemo.domain.point.repository;

import com.bootcamp.paymentdemo.domain.point.entity.PointDetail;
import com.bootcamp.paymentdemo.domain.point.entity.PointType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointDetailRepository extends JpaRepository<PointDetail, Long> {


    List<PointDetail> findAllByOrderId(String orderId);

    List<PointDetail> findAllByCustomerIdAndRemainAmountGreaterThanOrderByExpiredAtAsc(Long userId, int i);
<<<<<<< HEAD

    boolean existsByOrderId(String orderId);

    boolean existsByOrderIdAndType(String orderId, PointType type);
=======
>>>>>>> 151969f9676af4d71ebc2e19aa98dd6b2871bb5d
}
