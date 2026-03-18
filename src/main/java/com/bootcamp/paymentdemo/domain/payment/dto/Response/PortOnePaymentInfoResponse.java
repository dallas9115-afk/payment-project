package com.bootcamp.paymentdemo.domain.payment.dto.Response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PortOne 결제 단건 조회 응답 DTO
 *
 * PortOne 응답 필드는 버전/환경에 따라 조금 달라질 수 있어서
 * 알 수 없는 필드는 무시하고, 우리가 실제로 검증할 핵심 필드만 매핑합니다.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortOnePaymentInfoResponse {

    @JsonAlias({"id", "paymentId", "payment_id"})
    private String paymentId;

    // 결제 상태 (예: PAID, FAILED 등)
    private String status;

    @JsonAlias({"storeId", "store_id"})
    private String storeId;

    // 일부 응답은 amount.total, 일부는 amountTotal처럼 올 수 있어 둘 다 대응
    private Amount amount;

    @JsonAlias({"amountTotal"})
    private Long amountTotal;

    public Long resolveTotalAmount() {
        if (amount != null && amount.getTotal() != null) {
            return amount.getTotal();
        }
        return amountTotal;
    }

    public boolean isPaidStatus() {
        return status != null && "PAID".equalsIgnoreCase(status);
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Amount {
        private Long total;
    }
}
