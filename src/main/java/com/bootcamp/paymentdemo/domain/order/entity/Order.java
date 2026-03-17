package com.bootcamp.paymentdemo.domain.order.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name="orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 주문 번호
    private String orderNumber;

    // 사용자 아이디 나중에 추가

    // 주문 상태
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    public Order(Long userId, String orderNumber, Integer totalAmount, OrderStatus status) {
        this.orderNumber = orderNumber;
        this.status = status;

    }

}
