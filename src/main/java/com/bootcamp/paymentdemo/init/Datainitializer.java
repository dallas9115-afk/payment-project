package com.bootcamp.paymentdemo.init;

import com.bootcamp.paymentdemo.domain.product.entity.Product;
import com.bootcamp.paymentdemo.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Datainitializer implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Override
    public void run(String...args){


        // 더미 데이터가 쌓이지 않게 방지.
        if (productRepository.count() > 0) {
            return;
        }

        productRepository.save(new Product(
                "포카칩",
                1700,
                100,
                "짭짤한 감자칩",
                "과자"
        ));

        productRepository.save(new Product(
                "새우깡",
                1500,
                120,
                "바삭한 새우 과자",
                "과자"
        ));

        productRepository.save(new Product(
                "초코파이",
                5000,
                80,
                "초코와 마시멜로 과자",
                "과자"
        ));

        productRepository.save(new Product(
                "빼빼로",
                1800,
                90,
                "초콜릿 막대 과자",
                "과자"
        ));

        productRepository.save(new Product(
                "오징어집",
                2000,
                70,
                "오징어 맛 스낵",
                "과자"
        ));
    }

    }

