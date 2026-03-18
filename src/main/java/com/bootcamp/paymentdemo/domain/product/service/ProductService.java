package com.bootcamp.paymentdemo.domain.product.service;

import com.bootcamp.paymentdemo.domain.product.dto.Response.ProductOneResponse;
import com.bootcamp.paymentdemo.domain.product.entity.Product;
import com.bootcamp.paymentdemo.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ProductService {

    public final ProductRepository productRepository;

    // 단건 조회
    @Transactional(readOnly = true)
    public ProductOneResponse productGetOne(Long productId){
        Product product =productRepository.findById(productId).orElseThrow(
                ()-> new IllegalArgumentException("해당 상품이 없습니다.")
        );
        return new ProductOneResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.getDescription(),
                product.getStatus(),
                product.getCategory()
        );
    }

    // 모두 조회
    @Transactional(readOnly = true)
    public List<ProductOneResponse> productGetAll() {
        List<Product> products=productRepository.findAll();

        List<ProductOneResponse> dtos=new ArrayList<>();
        for(Product product: products){
            ProductOneResponse dto=new ProductOneResponse(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    product.getStock(),
                    product.getDescription(),
                    product.getStatus(),
                    product.getCategory()
            );
            dtos.add(dto);
        }
        return dtos;
    }
}
