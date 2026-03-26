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

    /**
     * 실패 사유 필드들 (응답 포맷 차이를 고려해 여러 키를 허용)
     * - API 버전/결제수단/상태에 따라 어느 필드에 담길지 달라질 수 있습니다.
     */
    @JsonAlias({"message", "reason"})
    private String message;

    @JsonAlias({"failureReason", "failReason", "failure_reason"})
    private String failureReason;

    @JsonAlias({"pgMessage", "pg_message"})
    private String pgMessage;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Amount {
        private Long total;
    }


    public Long resolveTotalAmount() {
        if (amount != null && amount.getTotal() != null) {
            return amount.getTotal();
        }
        return amountTotal;
    }

    public boolean isPaidStatus() {
        return status != null && "PAID".equalsIgnoreCase(status);
    }

    public boolean isTerminalFailureStatus() {
        return status != null && (
                "FAILED".equalsIgnoreCase(status) ||
                        "CANCELLED".equalsIgnoreCase(status)
        );
    }

    public boolean isCancelledStatus() {
        return status != null && (
                "CANCELLED".equalsIgnoreCase(status) ||
                        "PARTIAL_CANCELLED".equalsIgnoreCase(status)
        );
    }

    /**
     * 포트원 응답에서 "사용자에게 보여줄 수 있는 실패 사유"를 최대한 추려서 반환합니다.
     */
    public String resolveFailureReason() {
        if (failureReason != null && !failureReason.isBlank()) {
            return failureReason;
        }
        if (pgMessage != null && !pgMessage.isBlank()) {
            return pgMessage;
        }
        if (message != null && !message.isBlank()) {
            return message;
        }
        return "포트원에서 실패 사유를 제공하지 않았습니다.";
    }
}
