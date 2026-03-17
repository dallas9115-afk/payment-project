package com.bootcamp.paymentdemo.domain.order.entity;


import com.bootcamp.paymentdemo.domain.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name="order_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // 주문과 매핑 주문은 하나지만 주문 상품은 여러개 일 수 있다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orderId",nullable = false)
    private Order order;


  // 하나의 상품(Product)은 여러 개의 주문상품(OrderItem)에 포함될 수 있다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "productId", nullable = false)
    private Product product;



    // 주문 시점의 상품명
    @Column(nullable = false)
    private String productName;

    // 주문 시점의 상품 가격
    @Column(nullable = false)
    private Integer productPrice;

    // 주문 수량
    @Column(nullable = false)
    private Integer quantity;


    public OrderItem(Product product, Integer quantity) {
        this.product = product;
        this.productName = product.getName();
        this.productPrice = product.getPrice();
        this.quantity = quantity;
    }

}
