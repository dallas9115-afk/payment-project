package com.bootcamp.paymentdemo.domain.customer.entity;

import com.bootcamp.paymentdemo.domain.customer.enums.Grade;
//import com.bootcamp.paymentdemo.domain.point.entity.PointDetail;
//import com.bootcamp.paymentdemo.domain.point.entity.PointHistory;
//import com.bootcamp.paymentdemo.domain.point.entity.PointType;
import com.bootcamp.paymentdemo.domain.point.entity.PointDetail;
import com.bootcamp.paymentdemo.domain.point.entity.PointHistory;
import com.bootcamp.paymentdemo.domain.point.entity.PointType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

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
    @Size(min = 8, max = 200)
    private String password;

    // 고객 전화번호
    @NotBlank(message = "전화번호는 필수입니다")
    @Pattern(
            regexp = "^\\d{3}-\\d{3,4}-\\d{4}$",
            message = "전화번호 형식이 잘못되었습니다.(예시: 010-0000-0000)"
    )
    private String phoneNumber;

    // 고객 등급
    @Enumerated(EnumType.STRING)
    private Grade grade = Grade.NORMAL; // 디폴트 값(NORMAL)

//    // 고객 포인트   <- 스냅샷으로 아래 변수명으로 변경하였습니다 :D
//    private int points = 0; // 디폴트 값(0P)

    // [이식] 포인트 잔액 스냅샷 (int에서 Long으로 변경 권장 - 정합성 통일)
    @Column(nullable = false)
    private Long currentPoint = 0L;



    @Builder
    private Customer(String name, String email, String password, String phoneNumber, Grade grade, Long currentPoint) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.phoneNumber = phoneNumber;
        this.grade = (grade != null) ? grade : Grade.NORMAL;
        this.currentPoint = (currentPoint != null) ? currentPoint : 0L;
    }

    // --- [로직 이식 시작] --- by 임호진

    /**
     * 단순 차감 로직 (환불금 차감 등 직접 차감 시 사용)
     */
    public void deductPoint(Long amount) {
        if (amount <= 0) throw new IllegalArgumentException("차감 금액은 0보다 커야 합니다.");
        if (this.currentPoint < amount) {
            throw new IllegalStateException("잔액이 부족합니다. (현재: " + this.currentPoint + ")");
        }
        this.currentPoint -= amount;
    }

    /**
     * 단순 적립 로직
     */
    public void addPoint(Long amount) {
        if (amount <= 0) throw new IllegalArgumentException("적립 금액은 0보다 커야 합니다.");
        this.currentPoint += amount;
    }

    /**
     * [핵심] 포인트 상세 내역과 함께 차감하며 이력을 생성하는 메서드
     */
    public PointHistory deductPointWithDetail(PointDetail detail,
                                              Long amountToDeduct,
                                              String orderId,
                                              PointType type,
                                              String reason) {
        if (this.currentPoint < amountToDeduct) {
            throw new IllegalStateException("잔액이 부족합니다.");
        }

        Long before = this.currentPoint;
        this.currentPoint -= amountToDeduct;
        Long after = this.currentPoint;

        return PointHistory.builder()
                .customer(this) // [주의] .user()에서 .customer()로 연관관계 필드명 변경 필요
                .pointDetail(detail)
                .amount(-amountToDeduct)
                .beforePoint(before)
                .afterPoint(after)
                .type(type)
                .orderId(orderId)
                .reason(reason)
                .build();
    }
}
