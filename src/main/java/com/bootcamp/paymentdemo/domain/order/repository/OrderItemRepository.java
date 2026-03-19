package com.bootcamp.paymentdemo.domain.order.repository;

import com.bootcamp.paymentdemo.domain.order.entity.Order;
import com.bootcamp.paymentdemo.domain.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem,Long> {
    List<OrderItem> findByOrder(Order order);

    // 값이 존재 하지 않을 수 도 있다. Optional 사용.
    Optional<OrderItem> findByOrderOrderIdAndProductId(String orderId, Long productId);
}
