package com.bootcamp.paymentdemo.domain.subscription2.entity;

import com.bootcamp.paymentdemo.domain.payment.enums.PaymentMethodStatus;
import com.bootcamp.paymentdemo.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "payment_methods", indexes = {
        @Index(name = "idx_payment_method_customer", columnList = "customerId, isDefault")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class PaymentMethod extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long customerId;
    private String billingKey;
    private String customerUid;

    private String cardBrand;
    private String last4;

    private boolean isDefault;

    @Enumerated(EnumType.STRING)
    private PaymentMethodStatus status; //ACTIVE, DELETED

    public void unsetDefault() {
        this.isDefault = false;
    }


}
