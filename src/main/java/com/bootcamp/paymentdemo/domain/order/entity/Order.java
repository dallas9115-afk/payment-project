package com.bootcamp.paymentdemo.domain.order.entity;


import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name="orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

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

    public Order(String orderId, Customer customer, String orderNumber) {
        this.orderId=orderId;
        this.customer = customer;
        this.orderNumber = orderNumber;
        this.totalAmount=0;
        this.status = OrderStatus.PENDING;
    }
    public void changeTotalAmount(Integer totalAmount) {
        this.totalAmount = totalAmount;
    }

    public void changeStatus(OrderStatus status) {
        this.status = status;
    }
}
