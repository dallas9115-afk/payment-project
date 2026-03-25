package com.bootcamp.paymentdemo.domain.customer.service;

import com.bootcamp.paymentdemo.domain.customer.dto.request.CustomerSignupRequest;
import com.bootcamp.paymentdemo.domain.customer.dto.response.CustomerSignupResponse;
import com.bootcamp.paymentdemo.domain.customer.entity.Customer;

public interface CustomerService {
    CustomerSignupResponse create(CustomerSignupRequest request);

    CustomerSignupResponse findByEmail(String email);

    Customer findCustomerByEmail(String email);

    Customer findById(Long customerId);
}
