package com.bootcamp.paymentdemo.domain.payment.service;

import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.payment.dto.Request.PaymentMethodCreateRequest;
import com.bootcamp.paymentdemo.domain.payment.entity.PaymentMethod;
import com.bootcamp.paymentdemo.domain.payment.repository.PaymentMethodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentAccessValidator paymentAccessValidator;

    public PaymentMethod savePaymentMethod(Authentication authentication, PaymentMethodCreateRequest request){
        Customer customer = paymentAccessValidator.getAuthenticatedCustomer(authentication);
        return paymentMethodRepository.save(PaymentMethod.create(customer,request));

    }

}
