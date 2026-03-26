package com.bootcamp.paymentdemo.init;

import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.enums.Rank;
import com.bootcamp.paymentdemo.domain.customer.repository.CustomerRepository;
import com.bootcamp.paymentdemo.domain.product.entity.Product;
import com.bootcamp.paymentdemo.domain.product.repository.ProductRepository;
import com.bootcamp.paymentdemo.domain.subscription.entity.BillingInterval;
import com.bootcamp.paymentdemo.domain.subscription.entity.PlanLevel;
import com.bootcamp.paymentdemo.domain.subscription.entity.PlanStatus;
import com.bootcamp.paymentdemo.domain.subscription.entity.SubscriptionPlan;
import com.bootcamp.paymentdemo.domain.subscription.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Datainitializer implements CommandLineRunner {

    private static final String TEST_EMAIL = "admin@test.com";

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository2;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String...args){
        seedTestCustomer();
        seedProducts();
        seedSubscriptionPlans2();
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

    private void seedSubscriptionPlans2() {
        if (subscriptionPlanRepository2.count() > 0) {
            return;
        }

        subscriptionPlanRepository2.save(SubscriptionPlan.builder()
                .name("BASIC")
                .price(30000L)
                .status(PlanStatus.ACTIVE)
                .billingInterval(BillingInterval.MONTHLY)
                .interval(BillingInterval.MONTHLY)
                .trialPeriodDays(0)
                .level(PlanLevel.BASIC)
                .description("필수 기능을 부담 없는 가격으로 이용할 수 있는 베이직 월간 구독 플랜")
                .build());

        subscriptionPlanRepository2.save(SubscriptionPlan.builder()
                .name("STANDARD")
                .price(40000L)
                .status(PlanStatus.ACTIVE)
                .billingInterval(BillingInterval.MONTHLY)
                .interval(BillingInterval.MONTHLY)
                .trialPeriodDays(0)
                .level(PlanLevel.STANDARD)
                .description("기본을 넘어 더 다양한 기능과 향상된 서비스를 제공하는 스탠다드 월간 구독 플랜")
                .build());

        subscriptionPlanRepository2.save(SubscriptionPlan.builder()
                .name("VIP")
                .price(70000L)
                .status(PlanStatus.ACTIVE)
                .billingInterval(BillingInterval.MONTHLY)
                .interval(BillingInterval.MONTHLY)
                .trialPeriodDays(0)
                .level(PlanLevel.VIP)
                .description("모든 프리미엄 혜택과 최상의 서비스를 경험할 수 있는 VIP 월간 구독 플랜")
                .build());
    }
}
