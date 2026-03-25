package com.bootcamp.paymentdemo.init;

import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.enums.Rank;
import com.bootcamp.paymentdemo.domain.customer.repository.CustomerRepository;
import com.bootcamp.paymentdemo.domain.product.entity.Product;
import com.bootcamp.paymentdemo.domain.product.repository.ProductRepository;
import com.bootcamp.paymentdemo.domain.subscription.entity.PlanLevel;
import com.bootcamp.paymentdemo.domain.subscription2.entity.BillingInterval2;
import com.bootcamp.paymentdemo.domain.subscription2.entity.PlanLevel2;
import com.bootcamp.paymentdemo.domain.subscription2.entity.PlanStatus2;
import com.bootcamp.paymentdemo.domain.subscription2.entity.SubscriptionPlan2;
import com.bootcamp.paymentdemo.domain.subscription2.repository.SubscriptionPlanRepository2;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class Datainitializer implements CommandLineRunner {

    private static final String TEST_EMAIL = "admin@test.com";

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final SubscriptionPlanRepository2 planRepository;

    @Override
    public void run(String...args){
        seedTestCustomer();
        seedProducts();
        seedPlans();
    }

    private void seedTestCustomer() {
        if (customerRepository.existsByEmail(TEST_EMAIL)) {
            return;
        }

        customerRepository.save(
                Customer.builder()
                        .name("테스트 관리자")
                        .email(TEST_EMAIL)
                        .password(passwordEncoder.encode("admin1234"))
                        .phoneNumber("010-1234-5678")
                        .rank(Rank.NORMAL)
                        .currentPoint(50000L)
                        .build()
        );
    }

    private void seedProducts() {
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

    private void seedPlans() {
        if (planRepository.count() > 0) {
            return;
        }

        List<SubscriptionPlan2> defaultPlans = Arrays.stream(PlanLevel2.values())
                .map(level -> SubscriptionPlan2.builder()
                        .name(level.getValue())
                        .price((long) level.getAdditionalAmount())
                        .description(level.getContent())
                        .interval(BillingInterval2.MONTHLY)
                        .status(PlanStatus2.ACTIVE)
                        .build())
                .collect(Collectors.toList());

        planRepository.saveAll(defaultPlans);
    }
}

