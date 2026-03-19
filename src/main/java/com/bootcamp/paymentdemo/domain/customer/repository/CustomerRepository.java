package com.bootcamp.paymentdemo.domain.customer.repository;

import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE) //비관적 lock 설정
    @Query("select u from Customer  u where u.id = :id")
    Optional<Customer> findByIdWithLock(Long customerId);
}
