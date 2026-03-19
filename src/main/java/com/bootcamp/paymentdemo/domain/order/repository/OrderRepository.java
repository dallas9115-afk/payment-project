package com.bootcamp.paymentdemo.domain.order.repository;

import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order,Long> {
    Optional<Order> findByOrderId(String orderId);

    List<Order> findByCustomer(Customer customer);

//    Long sumPaidAmountByUserId(Long userId);


    // 형민님 제가 해당 메서드가 필요해서... 잠시 추가하겠읍니다.. 리팩터링 이후에 customerId를 사용해야되서링..
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.customer.id = :customerId")
    Long sumPaidAmountByCustomerId(@Param("customerId") Long customerId);
}
