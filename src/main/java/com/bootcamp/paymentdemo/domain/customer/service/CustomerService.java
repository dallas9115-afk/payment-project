package com.bootcamp.paymentdemo.domain.customer.service;

import com.bootcamp.paymentdemo.domain.customer.dto.request.CustomerSignupRequest;
import com.bootcamp.paymentdemo.domain.customer.dto.response.CustomerSignupResponse;
import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.enums.Grade;
import com.bootcamp.paymentdemo.domain.customer.repository.CustomerRepository;
import com.bootcamp.paymentdemo.global.error.CommonError;
import com.bootcamp.paymentdemo.global.error.CommonException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {
    private final CustomerRepository customerRepository;

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
                        .password(request.password())
                        .phoneNumber(request.phoneNumber())
                        .grade(Grade.NORMAL)
                        .points(0)
                        .build()
        );
        return CustomerSignupResponse.from(customer);
    }
}
