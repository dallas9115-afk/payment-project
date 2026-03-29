package com.bootcamp.paymentdemo.domain.payment.dto.Request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 포트원 V2 웹훅 데이터를 수신하기 위한 DTO
 *
 * 포트원 V2 실제 웹훅 바디 예시:
 * {
 *   "tx_id": "019d3a20-...",
 *   "payment_id": "pay-...",
 *   "status": "Ready" | "Paid" | "Failed" | ...
 * }
 *
 * - 필드가 snake_case 평탄(flat) 구조임에 주의
 * - 중첩된 data 객체가 아님
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortOneWebhookRequest {

    @JsonProperty("tx_id")
    private String txId;           // 포트원 트랜잭션 고유 ID

    @JsonProperty("payment_id")
    private String paymentId;      // 우리 시스템의 결제 고유 ID (가장 중요)

    private String status;         // 결제 상태: Ready, Paid, Failed, ...

    /**
     * 결제 완료(Paid) 상태인지 확인
     */
    public boolean isPaidStatus() {
        return "Paid".equalsIgnoreCase(status);
    }
}
