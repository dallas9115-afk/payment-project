package com.bootcamp.paymentdemo.domain.point.entity;


import com.bootcamp.paymentdemo.domain.user.entity.User;
import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.*;
import jakarta.persistence.Entity;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "point_histories")
public class PointHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_detail_id")
    private PointDetail pointDetail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointStatus type;

    @Column(nullable = false)
    private Long amount;   // 정합성을 위해 long 사용

    @Column(nullable = false)
    private Long beforePoint;

    @Column(nullable = false)
    private Long afterPoint;

    @Column
    private String orderId;

    @Column
    private String reason;


}
