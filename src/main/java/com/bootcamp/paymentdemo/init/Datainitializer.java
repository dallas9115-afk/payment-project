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
    private static final Long ADMIN_INIT_POINT = 5000000L;

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String...args) {
        seedTestCustomer();
        seedProducts();
        seedSubscriptionPlans();
    }

    private void seedTestCustomer() {
        if (customerRepository.existsByEmail(TEST_EMAIL)) {
            Customer customer = customerRepository.findByEmail(TEST_EMAIL)
                    .orElseThrow(() -> new IllegalArgumentException("관리자 계정을 찾을 수 없습니다."));

            Long currentPoint = customer.getCurrentPoint();
            if (currentPoint < ADMIN_INIT_POINT) {
                customer.addPoint(ADMIN_INIT_POINT - currentPoint);
            } else if (currentPoint > ADMIN_INIT_POINT) {
                customer.deductPoint(currentPoint - ADMIN_INIT_POINT);
            }
            return;
        }

        customerRepository.save(
                Customer.builder()
                        .name("테스트 관리자")
                        .email(TEST_EMAIL)
                        .password(passwordEncoder.encode("admin1234"))
                        .phoneNumber("010-1234-5678")
                        .rank(Rank.NORMAL)
                        .currentPoint(ADMIN_INIT_POINT)
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

    private void seedSubscriptionPlans() {
        if (subscriptionPlanRepository.count() > 0) {
            return;
        }

        subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .name("BASIC")
                .price(30000L)
                .status(PlanStatus.ACTIVE)
                .billingInterval(BillingInterval.MONTHLY)
                .interval(BillingInterval.MONTHLY)
                .level(PlanLevel.BASIC)
                .description("베이직 구독 플랜")
                .content("필수 기능을 부담 없는 가격으로 이용할 수 있는 베이직 구독 플랜")
                .build());

        subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .name("STANDARD")
                .price(40000L)
                .status(PlanStatus.ACTIVE)
                .billingInterval(BillingInterval.MONTHLY)
                .interval(BillingInterval.MONTHLY)
                .level(PlanLevel.STANDARD)
                .description("스탠다드 구독 플랜")
                .content("더 많은 기능과 높은 만족도를 혜택으로 누릴 수 있는 스탠다드 구독 플랜")
                .build());

        subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .name("VIP")
                .price(70000L)
                .status(PlanStatus.ACTIVE)
                .billingInterval(BillingInterval.MONTHLY)
                .interval(BillingInterval.MONTHLY)
                .level(PlanLevel.VIP)
                .description("VIP 구독 플랜")
                .content("모든 프리미엄 혜택과 최상의 서비스를 경험할 수 있는 VIP 구독 플랜")
                .build());
    }
}
