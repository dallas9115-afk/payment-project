package com.bootcamp.paymentdemo.domain.order.repository;

import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order,Long> {
    Optional<Order> findByOrderId(String orderId);

    List<Order> findByCustomer(Customer customer);
}
