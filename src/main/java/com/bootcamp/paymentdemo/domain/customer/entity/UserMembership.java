package com.bootcamp.paymentdemo.domain.customer.entity;

import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;


@Getter
@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@Table(name = "user_memberships")
public class UserMembership extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY) // 유저 한 명당 하나의 멤버십 상태
    @JoinColumn(name = "customer_id", unique = true)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_grade_policy_id", nullable = false)
    private com.bootcamp.paymentdemo.domain.customer.entity.MembershipGradePolicy gradePolicy;

    @Column(nullable = false)
    private Long totalPaidAmount = 0L; // 누적 결제액 (등급 산정 기준)

    @Column(nullable = false)
    private Double currentPointRate; // 현재 적용 중인 적립률 (Snapshot)

    //  등급 업데이트 로직 <- 결제시 호출하는 메서드임
    public void updateMembership(com.bootcamp.paymentdemo.domain.customer.entity.MembershipGradePolicy newPolicy, Long newTotalAmount) {

        // 1. 누적 금액 업데이트(금액은 항상 이전보다 크거나 같아야함. 조건식)
        if (newTotalAmount < this.totalPaidAmount) {
            throw new IllegalArgumentException("누적 결제 금액은 줄어들 수 없습니다.");
        }

        this.totalPaidAmount = newTotalAmount;

        // 2. 등급 정책 변경 감지 및 업데이트
        if (newPolicy != null && !this.gradePolicy.equals(newPolicy)) {

            // 등급이 바뀌면 등급에 관한 적립률도 재업데이트 됩니다.
            this.gradePolicy = newPolicy;
            this.currentPointRate = newPolicy.getPointRate();
        }

    }

    // 단순 등급 업데이트
    public void updateGrade(MembershipGradePolicy newPolicy) {
        if (newPolicy != null) {
            this.gradePolicy = newPolicy;
            this.currentPointRate = newPolicy.getPointRate();
        }
    }



}
