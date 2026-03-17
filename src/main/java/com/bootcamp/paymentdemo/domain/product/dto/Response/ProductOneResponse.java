package com.bootcamp.paymentdemo.domain.product.dto.Response;

import com.bootcamp.paymentdemo.domain.product.entity.ProductStatus;
import lombok.Getter;

@Getter
public class ProductOneResponse {

    private final Long id;
    private final String name;      // 상품명
    private final int price;        // 판매가
    private final int stock;        // 재고
    private final String description; // 설명
    private final ProductStatus status; // 상태
    private final String category;         // 카테고리

    public ProductOneResponse(Long id, String name, int price, int stock, String description, ProductStatus status, String category) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.description = description;
        this.status = status;
        this.category = category;
    }
}

