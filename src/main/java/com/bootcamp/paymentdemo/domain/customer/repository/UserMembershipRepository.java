package com.bootcamp.paymentdemo.domain.customer.repository;

import com.bootcamp.paymentdemo.domain.customer.entity.UserMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserMembershipRepository extends JpaRepository<UserMembership, Long> {

    //고객 ID로 현재 멤버십 상태 조회
    Optional<UserMembership> findByCustomerId(Long customerId);
}
