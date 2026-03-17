package com.bootcamp.paymentdemo.domain.point.entity;


import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.*;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "point_histories")
public class PointHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long pointDetailId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointStatus type;

    @Column(nullable = false)
    private Integer amount;

    @Column
    private String orderId;

}
