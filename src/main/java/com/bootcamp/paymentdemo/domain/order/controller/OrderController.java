package com.bootcamp.paymentdemo.domain.order.controller;


import com.bootcamp.paymentdemo.domain.order.dto.Request.CreateOrderRequest;
import com.bootcamp.paymentdemo.domain.order.dto.Response.CreateOrderResponse;
import com.bootcamp.paymentdemo.domain.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders/v1")
public class OrderController {

    private final OrderService orderService;
//    // 주문 생성
//    @PostMapping("/orders")
//    public ResponseEntity<CreateOrderResponse> createOrder(
//            @PathVariable Long customerId,
//            @RequestBody CreateOrderRequest request
//    )
//    {
//        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.save(customerId,request));
//
//    }
//
//    // 주문 단건 조회
//    @PostMapping("/orders/{orderId}")
//
//
//    // 주문 모두 조회
//    @PostMapping("/orders")


}
