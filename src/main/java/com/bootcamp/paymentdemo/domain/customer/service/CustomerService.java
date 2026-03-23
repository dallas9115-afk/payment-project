package com.bootcamp.paymentdemo.domain.customer.service;

import com.bootcamp.paymentdemo.domain.customer.dto.request.CustomerLoginRequest;
import com.bootcamp.paymentdemo.domain.customer.dto.request.CustomerSignupRequest;
import com.bootcamp.paymentdemo.domain.customer.dto.response.CustomerLoginResponse;
import com.bootcamp.paymentdemo.domain.customer.dto.response.CustomerSignupResponse;
import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.enums.Rank;
import com.bootcamp.paymentdemo.domain.customer.repository.CustomerRepository;
import com.bootcamp.paymentdemo.global.error.CommonError;
import com.bootcamp.paymentdemo.global.error.CommonException;
import com.bootcamp.paymentdemo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {
    private final CustomerRepository customerRepository;
//    private final MembershipService membershipService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    // 고객 회원가입
    @Transactional
    public CustomerSignupResponse signUp(CustomerSignupRequest request) {
        if(customerRepository.existsByEmail(request.email())) {
            throw new CommonException(CommonError.DUPLICATE_EMAIL);
        }
        Customer customer = customerRepository.save(
                Customer.builder()
                        .name(request.name())
                        .email(request.email())
                        .password(passwordEncoder.encode(request.password()))
                        .phoneNumber(request.phoneNumber())
                        .rank(Rank.NORMAL)
                        .currentPoint(0L)
                        .build()
        );

//        membershipService.createDefaultMembership(customer);

        return CustomerSignupResponse.from(customer);
    }
    
    // 고객 로그인
    @Transactional(readOnly = true)
    public CustomerLoginResponse login(CustomerLoginRequest request) {
        Customer customer = customerRepository.findByEmail(request.email()).orElseThrow(
                () -> new CommonException(CommonError.LOGIN_FAILED)
        );

        if (!passwordEncoder.matches(request.password(), customer.getPassword())) {
            throw new CommonException(CommonError.LOGIN_FAILED);
        }

        String token = jwtTokenProvider.createToken(
                customer.getId(),
                customer.getEmail()
        );
        return CustomerLoginResponse.from(customer, token);
    }
}
