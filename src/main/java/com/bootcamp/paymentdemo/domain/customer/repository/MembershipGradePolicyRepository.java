package com.bootcamp.paymentdemo.domain.customer.repository;

import com.bootcamp.paymentdemo.domain.customer.entity.MembershipGradePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MembershipGradePolicyRepository extends JpaRepository<MembershipGradePolicy, Long> {

    // 1. 회원가입 시 기본 등급 조회를 위해 사용
    Optional<MembershipGradePolicy> findByGradeCode(String gradeCode);

    // 2. [핵심] 누적 금액에 맞는 정책 조회 (BETWEEN 활용)
    @Query("SELECT p FROM MembershipGradePolicy p " +
            "WHERE : totalAmount BETWEEN p.minPaidAmount AND p.maxPaidAmount " +
            "AND p.isActive = true")
    Optional<MembershipGradePolicy> findSuitablePolicy(@Param("totalAmount") Long totalAmount);

    @Query(" SELECT p From MembershipGradePolicy p " +
           "WHERE :amount BETWEEN p.minPaidAmount AND p.maxPaidAmount " +
           "AND p.isActive = true")
    Optional<MembershipGradePolicy> findByAmountInRange(@Param("amount") Long amount);

    Optional<MembershipGradePolicy> findTopByMinPaidAmountLessThanEqualOrderByMinPaidAmountDesc(Long amount);
}
