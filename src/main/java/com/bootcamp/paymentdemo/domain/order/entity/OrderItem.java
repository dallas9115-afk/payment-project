package com.bootcamp.paymentdemo.domain.order.entity;


import com.bootcamp.paymentdemo.domain.product.entity.Product;
import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "order_items",
        indexes = {
                @Index(
                        name = "idx_order_items_order",
                        columnList = "order_id"
                ),
                @Index(
                        name = "idx_order_items_order_product",
                        columnList = "order_id, product_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // 주문과 매핑 주문은 하나지만 주문 상품은 여러개 일 수 있다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id",nullable = false)
    private Order order;


  // 하나의 상품(Product)은 여러 개의 주문상품(OrderItem)에 포함될 수 있다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
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


    public OrderItem(Order order,Product product, Integer quantity) {
       this.order=order;
        this.product = product;
        this.productName = product.getName();
        this.productPrice = product.getPrice();
        this.quantity = quantity;
    }

}
