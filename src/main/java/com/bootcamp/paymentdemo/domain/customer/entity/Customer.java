package com.bootcamp.paymentdemo.domain.customer.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "customers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {

    // 고객 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 고객 이름
    @NotBlank(message = "이름은 필수 입력값입니다.")
    @Column(nullable = false, length = 50)
    @Size(min = 1, max = 50)
    private String name;

    // 고객 이메일
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Column(nullable = false, unique = true, length = 320)
    @Size(min = 1, max = 320)
    private String email;

    // 고객 비밀번호
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    @Column(name = "password_hash", nullable = false, length = 200)
    @Size(min = 1, max = 200)
    private String password;

    // 고객 전화번호
    @NotBlank(message = "전화번호는 필수입니다")
    @Pattern(
            regexp = "^\\d{3}-\\d{3,4}-\\d{4}$",
            message = "전화번호 형식이 잘못되었습니다.(예시: 010-0000-0000)"
    )
    private String phoneNumber;

    // 고객 등급
//    @Enumerated(EnumType.STRING)
//    private Grade grade = GradeType.NORMAL; // 디폴트 값(NORMAL)
//
//    // 고객 포인트
//    private int points = 0; // 디폴트 값(0P)
//
//    @Builder
//    private Customer(String name, String email, String password, String phoneNumber, Grade grade, Integer points) {
//        this.name = name;
//        this.email = email;
//        this.password = password;
//        this.phoneNumber = phoneNumber;
//        this.grade = grade;
//        this.points = points;
//    }
}
