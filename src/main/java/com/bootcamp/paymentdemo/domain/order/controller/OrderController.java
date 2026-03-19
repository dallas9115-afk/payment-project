package com.bootcamp.paymentdemo.domain.order.controller;


import com.bootcamp.paymentdemo.domain.order.dto.Request.CreateOrderRequest;
import com.bootcamp.paymentdemo.domain.order.dto.Response.CreateOrderResponse;
import com.bootcamp.paymentdemo.domain.order.dto.Response.OrderDetailResponse;
import com.bootcamp.paymentdemo.domain.order.dto.Response.OrderListResponse;
import com.bootcamp.paymentdemo.domain.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders/v1")
public class OrderController {

    private final OrderService orderService;
    // 주문 생성
    // orderId를 프론트엔드 규격에따라서 새롭게 생성햇기때문에 String orderId로 받는다.
    @PostMapping("/orders/{customerId}")
    public ResponseEntity<CreateOrderResponse> createOrder(
            @PathVariable Long customerId,
            @Valid @RequestBody CreateOrderRequest request
    )
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.save(customerId,request));

    }



     //    @GetMapping("/orders/{orderId}")
    // 주문 상세 조회
        @GetMapping("/orders/{orderId}/items/{productId}")
    public ResponseEntity<OrderDetailResponse> getOrder(
                @PathVariable String orderId,
                @PathVariable Long productId

    )
    {
        return ResponseEntity.ok(orderService.getOrderDetail(orderId,productId));

    }

    // 주문 목록(내역) 조회
    @GetMapping("/orders/customer/{customerId}")
    public ResponseEntity<List<OrderListResponse>> getOrderList(
            @PathVariable Long customerId
    ) {
        return ResponseEntity.ok(orderService.getOrderList(customerId));
    }

}
