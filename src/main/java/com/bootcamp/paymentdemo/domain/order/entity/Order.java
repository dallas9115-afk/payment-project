package com.bootcamp.paymentdemo.domain.order.entity;


import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
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

    @Column(nullable = false, unique = true)
    private String orderId;

    // 사용자 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id",nullable = false)
    private Customer customer;

    // 주문 번호
    private String orderNumber;

    private Integer totalAmount;


    // 주문 상태
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    public Order(String orderId, Customer customer, String orderNumber, Integer totalAmount, OrderStatus status) {
        this.orderId=orderId;
        this.customer = customer;
        this.orderNumber = orderNumber;
        this.totalAmount = totalAmount;
        this.status = status;
    }

}
