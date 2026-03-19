package com.bootcamp.paymentdemo.domain.product.controller;


import com.bootcamp.paymentdemo.domain.product.dto.Response.ProductOneResponse;
import com.bootcamp.paymentdemo.domain.product.repository.ProductRepository;
import com.bootcamp.paymentdemo.domain.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products/v1")
public class ProductController {

    private final ProductService productService;


    // 이거는 선택사항 일단 구현은 해놨다.
    //상품 단건 조회 필요없을수도 있다.
    //결제 화면을 띄울것이다.
    //서버 요청이 2개도  가능.

    // 단건 조회
    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductOneResponse>getOneProduct(
            @PathVariable Long productId
    ){
        return ResponseEntity.status(HttpStatus.OK).body(productService.productGetOne(productId));
    }


    // 모두 조회
    @GetMapping("/products")
    public ResponseEntity<List<ProductOneResponse>> getAll()
    {
        return ResponseEntity.status(HttpStatus.OK).body(productService.productGetAll());
    }
}
