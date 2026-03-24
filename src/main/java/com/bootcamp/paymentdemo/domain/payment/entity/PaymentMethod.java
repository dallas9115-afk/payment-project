package com.bootcamp.paymentdemo.domain.payment.entity;

import com.bootcamp.paymentdemo.domain.customer.entity.Customer;
import com.bootcamp.paymentdemo.domain.payment.dto.Request.PaymentMethodCreateRequest;
import com.bootcamp.paymentdemo.domain.payment.enums.PaymentMethodStatus;
import com.bootcamp.paymentdemo.domain.payment.enums.PgProvider;
import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "payment_methods",
        indexes = {
                @Index(name = "idx_payment_methods_customer_deleted_created", columnList = "customer_id,deleted_at,created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE payment_methods SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class PaymentMethod extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, unique = true)
    private String billingKey;

    @Column(nullable = false)
    private String customerUid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PgProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethodStatus status;

    @Column(nullable = false)
    private boolean isDefault;

    public static PaymentMethod create(Customer customer,  PaymentMethodCreateRequest request) {
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.customer = customer;
        paymentMethod.billingKey = request.billingKey();
        paymentMethod.customerUid =  request.customerUid();
        paymentMethod.provider = request.provider();
        paymentMethod.status = PaymentMethodStatus.ACTIVE;
        paymentMethod.isDefault = request.isDefault();
        return paymentMethod;
    }

    public void unsetDefault() {
        this.isDefault = false;
    }
}
