package com.bootcamp.paymentdemo.domain.product.repository;


import com.bootcamp.paymentdemo.domain.product.dto.Response.ProductOneResponse;
import com.bootcamp.paymentdemo.domain.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product,Long> {

}
