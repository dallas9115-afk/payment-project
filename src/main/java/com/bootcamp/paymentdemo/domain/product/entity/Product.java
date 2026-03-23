package com.bootcamp.paymentdemo.domain.product.entity;

import jakarta.persistence.*;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name="products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;      // 상품명
    private int price;        // 판매가
    private int stock;        // 재고
    private String description; // 설명

    @Enumerated(EnumType.STRING)
    private ProductStatus status; // 상태

    private String category;  // 카테고리

    public Product(String name, int price, int stock, String description, String category) {
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.description = description;
        this.status = ProductStatus.SALE;
        this.category = category;
    }

// 결제 성공 -> 재고 차감.
    public void decreaseStock(int quantity) {
        if (this.stock < quantity) {
            throw new IllegalStateException("재고가 부족합니다.");
        }
        this.stock -= quantity;
    }

    // 추후에 사용하기. 환불 -> 재고 증가.
    public void increaseStock(int quantity) {
        this.stock += quantity;
    }

}
