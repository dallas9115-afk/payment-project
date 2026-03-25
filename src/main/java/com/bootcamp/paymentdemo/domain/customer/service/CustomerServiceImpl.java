package com.bootcamp.paymentdemo.domain.customer.service;


import com.bootcamp.paymentdemo.domain.customer.dto.request.CustomerSignupRequest;
import com.bootcamp.paymentdemo.domain.customer.dto.response.CustomerSignupResponse;
import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.repository.CustomerRepository;
import com.bootcamp.paymentdemo.global.error.CommonError;
import com.bootcamp.paymentdemo.global.error.CommonException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public CustomerSignupResponse create(CustomerSignupRequest request) {

        boolean exists = customerRepository.existsByEmail(request.email());

        if (exists) {
            throw new CommonException(CommonError.DUPLICATE_EMAIL);
        }

        Customer customer = customerRepository.save(
                Customer.builder()
                        .name(request.name())
                        .email(request.email())
                        .password(passwordEncoder.encode(request.password()))
                        .phoneNumber(request.phoneNumber())
                        .build()
        );

        return CustomerSignupResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .email(customer.getEmail())
                .phoneNumber(customer.getPhoneNumber())
                .createdAt(customer.getCreatedAt())
                .build();
    }

    @Override
    public CustomerSignupResponse findByEmail(String email) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new CommonException(CommonError.CUSTOMER_NOT_FOUND));

        return CustomerSignupResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .email(customer.getEmail())
                .phoneNumber(customer.getPhoneNumber())
                .createdAt(customer.getCreatedAt())
                .build();
    }

    @Override
    public Customer findCustomerByEmail(String email) {
        return customerRepository.findByEmail(email)
                .orElseThrow(() -> new CommonException(CommonError.CUSTOMER_NOT_FOUND));
    }

}
