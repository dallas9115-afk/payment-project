package com.bootcamp.paymentdemo.domain.payment.service;

import com.bootcamp.paymentdemo.domain.order.entity.Order;
import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentAccessValidator {

    private final PaymentRepository paymentRepository;

    public void validateAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("인증된 사용자만 요청할 수 있습니다.");
        }

        Object principal = authentication.getPrincipal();
        if ("anonymousUser".equals(principal)) {
            throw new IllegalStateException("인증된 사용자만 요청할 수 있습니다.");
        }
    }

    public void validateOrderOwnership(Authentication authentication, Order order) {
        String authenticatedEmail = extractAuthenticatedEmail(authentication);
        String orderOwnerEmail = order.getCustomer().getEmail();

        if (orderOwnerEmail == null || !orderOwnerEmail.equals(authenticatedEmail)) {
            throw new IllegalStateException("본인 주문에 대해서만 진행할 수 있습니다.");
        }
    }

    public void validatePaymentOwnership(Authentication authentication, Payment payment) {
        validateOrderOwnership(authentication, payment.getOrder());
    }

    public Payment getAuthorizedPayment(Authentication authentication, String paymentId) {
        validateAuthenticated(authentication);

        Payment payment = paymentRepository.findByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("존재하지않는 paymentId입니다.")
        );
        validatePaymentOwnership(authentication, payment);
        return payment;
    }

    private String extractAuthenticatedEmail(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof String email && !email.isBlank()) {
            return email;
        }

        String authenticationName = authentication.getName();
        if (authenticationName != null && !authenticationName.isBlank()) {
            return authenticationName;
        }

        throw new IllegalStateException("인증 사용자 이메일을 확인할 수 없습니다.");
    }
}
