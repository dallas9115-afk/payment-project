package com.bootcamp.paymentdemo.domain.order.service;


import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.repository.CustomerRepository;
import com.bootcamp.paymentdemo.domain.order.dto.Request.CreateOrderRequest;
import com.bootcamp.paymentdemo.domain.order.dto.Response.CreateOrderResponse;
import com.bootcamp.paymentdemo.domain.order.repository.OrderItemRepository;
import com.bootcamp.paymentdemo.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository customerRepository;


//    @Transactional
//    public CreateOrderResponse save(Long customerId,CreateOrderRequest request) {
//        // 1. 고객 조회
//        Customer customer = customerRepository.findById(customerId)
//                .orElseThrow(() -> new IllegalArgumentException("고객 없음"));
//
//
//        return CreateOrderResponse();
//    }
}
