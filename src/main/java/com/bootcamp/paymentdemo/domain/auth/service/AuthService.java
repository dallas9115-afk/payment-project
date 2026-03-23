package com.bootcamp.paymentdemo.domain.auth.service;

import com.bootcamp.paymentdemo.domain.auth.dto.request.LoginRequestDto;
import com.bootcamp.paymentdemo.domain.auth.dto.request.RegisterRequestDto;
import com.bootcamp.paymentdemo.domain.auth.dto.response.LoginResponseDto;
import com.bootcamp.paymentdemo.domain.auth.dto.response.RegisterResponseDto;
import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.customer.enums.Rank;
import com.bootcamp.paymentdemo.domain.customer.repository.CustomerRepository;
import com.bootcamp.paymentdemo.domain.customer.service.MembershipService;
import com.bootcamp.paymentdemo.global.error.CommonError;
import com.bootcamp.paymentdemo.global.error.CommonException;
import com.bootcamp.paymentdemo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final CustomerRepository customerRepository;
    private final MembershipService membershipService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public RegisterResponseDto register(RegisterRequestDto request) {
        if (customerRepository.existsByEmail(request.email())) {
            throw new CommonException(CommonError.DUPLICATE_EMAIL);
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        Customer customer = new Customer(
                request.name(),
                request.email(),
                encodedPassword,
                request.phoneNumber(),
                Rank.NORMAL,
                0L
        );

        Customer savedCustomer = customerRepository.save(customer);

        membershipService.createDefaultMembership(savedCustomer);
        return new RegisterResponseDto(
                savedCustomer.getId(),
                savedCustomer.getName(),
                savedCustomer.getEmail(),
                savedCustomer.getPhoneNumber()
        );
    }

    @Transactional
    public LoginResponseDto login(LoginRequestDto request) {
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

        return new LoginResponseDto(
                token,
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getPhoneNumber()
        );
    }
}