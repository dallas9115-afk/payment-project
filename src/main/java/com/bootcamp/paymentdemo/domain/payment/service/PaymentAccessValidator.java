package com.bootcamp.paymentdemo.domain.payment.service;

import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.repository.CustomerRepository;
import com.bootcamp.paymentdemo.domain.order.entity.Order;
import com.bootcamp.paymentdemo.domain.payment.entity.Payment;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentAccessValidator {

    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;

    //로그인 했는지 검증
    public void validateAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("인증된 사용자만 요청할 수 있습니다.");
        }

        Object principal = authentication.getPrincipal();
        if ("anonymousUser".equals(principal)) {
            throw new IllegalStateException("인증된 사용자만 요청할 수 있습니다.");
        }
    }

    // 커스터머 본인주문인지 검증
    public void validateOrderOwnership(Authentication authentication, Order order) {
        String authenticatedEmail = extractAuthenticatedEmail(authentication);
        String orderOwnerEmail = order.getCustomer().getEmail();

        if (orderOwnerEmail == null || !orderOwnerEmail.equals(authenticatedEmail)) {
            throw new IllegalStateException("본인 주문에 대해서만 진행할 수 있습니다.");
        }
    }

    // 커스터머 본인주문인지 검증
    public void validatePaymentOwnership(Authentication authentication, Payment payment) {
        validateOrderOwnership(authentication, payment.getOrder());
    }

    // 종합검증
    public Payment getAuthorizedPayment(Authentication authentication, String paymentId) {
        validateAuthenticated(authentication);

        Payment payment = paymentRepository.findByPaymentId(paymentId).orElseThrow(
                () -> new IllegalArgumentException("존재하지않는 paymentId입니다.")
        );
        validatePaymentOwnership(authentication, payment);
        return payment;
    }

    // authentication에서 이메일뽑아서 이메일로 커스터머찾기
    public Customer getAuthenticatedCustomer(Authentication authentication) {
        validateAuthenticated(authentication);

        String authenticatedEmail = extractAuthenticatedEmail(authentication);
        return customerRepository.findByEmail(authenticatedEmail).orElseThrow(
                () -> new IllegalArgumentException("인증된 사용자를 찾을 수 없습니다. email=" + authenticatedEmail)
        );
    }

    // 이메일 가져오기(O어스2 가능)
    private String extractAuthenticatedEmail(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof OAuth2User oAuth2User) {
            Object email = oAuth2User.getAttributes().get("email");
            if (email instanceof String emailValue && !emailValue.isBlank()) {
                return emailValue;
            }
        }

        if (principal instanceof UserDetails userDetails) {
            String username = userDetails.getUsername();
            if (username != null && !username.isBlank()) {
                return username;
            }
        }

        if (principal instanceof String email && !email.isBlank() && !"anonymousUser".equals(email)) {
            return email;
        }

        String authenticationName = authentication.getName();
        if (authenticationName != null && !authenticationName.isBlank()
                && !"anonymousUser".equals(authenticationName)) {
            return authenticationName;
        }

        throw new IllegalStateException("인증 사용자 이메일을 확인할 수 없습니다.");
    }
}