package com.bootcamp.paymentdemo.domain.customer.entity;

import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Table(name = "membership_grade_policies")
public class MembershipGradePolicy extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String gradeCode; // NORMAL, VIP, VVIP

    @Column(nullable = false)
    private String gradeName; // 일반, 우수, 최우수

    @Column(nullable = false)
    private Long minPaidAmount; //

    @Column(nullable = false)
    private Long maxPaidAmount; //

    @Column(nullable = false)
    private Double pointRate; // 적립률 (0.05 = 5%)

    @Column(nullable = false)
    private Boolean isActive = true;
}